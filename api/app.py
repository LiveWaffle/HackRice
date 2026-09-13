import hashlib
import os
import secrets
import smtplib
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from uuid import uuid4

import jwt
import requests
from flask import Flask, jsonify, request

TOKEN_TTL_SECONDS = 45
APPROVAL_TTL_SECONDS = 20
MAX_VALIDATION_ATTEMPTS = 5
PERSONA_API_BASE_URL = "https://api.withpersona.com/api/v1"
PERSONA_VERSION = "2023-01-05"
GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
DEFAULT_GEMINI_MODEL = "gemini-2.0-flash"
FLAG_ALERT_RECIPIENT = "gilliamandrew22@gmail.com"
NORA_SYSTEM_INSTRUCTIONS = """
Nora is a medical assistant chatbot. Nora answers questions about health, symptoms, treatments, medications, and patient records only.

Scope Rules:
1. Answer medical questions only. This covers symptoms, conditions, treatments, medications, procedures, and general health guidance.
2. Reject non medical questions. If a user asks about weather, sports, coding, entertainment, or any topic outside medicine, tell them Nora handles medical questions only. Redirect them back to their health needs.
3. Use provided resources first. When a customer database or document is available, pull answers from that data before giving general medical information. Cite the specific record or field you used.
4. Never guess patient data. If the database does not contain an answer, say so directly. Do not invent patient details, test results, or history.
5. Stay within medical facts. Do not offer legal, financial, or personal life advice, even if the user connects it to a health topic.

Tone and Style:
1. Keep answers short and direct.
2. Use plain language. Avoid medical jargon unless the user uses it first.
3. State facts. Skip filler phrases and unnecessary caveats.
4. Address the user directly with "you" and "your."

Safety Rules:
1. Tell users to contact a doctor or call emergency services for urgent symptoms or crisis situations.
2. Do not diagnose. Describe possible causes and recommend professional evaluation.
3. Do not recommend specific drug dosages beyond what a provided resource states.
4. Flag any question that requires a licensed professional and direct the user to one.

Redirect Script:
When a question falls outside medicine, respond with a version of this line: "I handle medical questions only. Ask me about your symptoms, medications, or health records, and I will help."

Data Handling:
1. Treat all customer records as private. Do not share one customer's data with another.
2. Reference only the fields relevant to the question asked.
3. If asked to summarize a full record, give a factual summary without added interpretation.
""".strip()


def create_app():
    app = Flask(__name__)
    config = BackendConfig.from_env()

    @app.errorhandler(RuntimeError)
    @app.errorhandler(ValueError)
    def handle_expected_error(error):
        return jsonify({"error": str(error)}), 400

    @app.errorhandler(requests.HTTPError)
    def handle_upstream_error(error):
        response = error.response
        if response is None:
            return jsonify({"error": "Upstream request failed"}), 502

        message = read_upstream_error(response)
        return jsonify({"error": message}), response.status_code

    @app.get("/api/health")
    def health():
        return jsonify({"status": "ok"})

    @app.post("/api/patient/qr-token")
    def create_patient_qr_token():
        require_qr_config(config)
        patient_id = resolve_patient_id_from_session()
        now = datetime.now(timezone.utc)
        expires_at = now + timedelta(seconds=TOKEN_TTL_SECONDS)
        token_id = str(uuid4())
        nonce = secrets.token_urlsafe(32)

        payload = {
            "iss": "healthbridge-api",
            "aud": "healthbridge-doctor-scan",
            "jti": token_id,
            "iat": int(now.timestamp()),
            "exp": int(expires_at.timestamp()),
            "nonce": nonce,
        }
        signed_token = jwt.encode(payload, config.private_key_pem, algorithm="RS256")

        supabase_patch(
            config,
            "qr_access_tokens",
            {"active": "eq.true", "patient_id": f"eq.{patient_id}"},
            {"active": False},
        )
        supabase_insert(
            config,
            "qr_access_tokens",
            {
                "id": token_id,
                "patient_id": patient_id,
                "token_hash": sha256_text(signed_token),
                "nonce_hash": sha256_text(nonce),
                "issued_at": now.isoformat(),
                "expires_at": expires_at.isoformat(),
                "active": True,
            },
        )

        return jsonify(
            {
                "tokenId": token_id,
                "signedToken": signed_token,
                "issuedAtMillis": millis(now),
                "expiresAtMillis": millis(expires_at),
            }
        )

    @app.post("/api/doctor/validate-token")
    def validate_doctor_scan():
        require_qr_config(config)
        body = request.get_json(force=True)
        signed_token = required(body, "signedToken")
        doctor_id = resolve_doctor_id_from_session()
        hospital_id = required(body, "hospitalId")
        device_fingerprint = required(body, "deviceFingerprint")
        device_info = required(body, "deviceInfo")

        try:
            payload = jwt.decode(
                signed_token,
                config.public_key_pem,
                algorithms=["RS256"],
                audience="healthbridge-doctor-scan",
                issuer="healthbridge-api",
            )
        except jwt.ExpiredSignatureError:
            return reject_scan(config, None, None, hospital_id, "expired")
        except jwt.InvalidTokenError:
            return reject_scan(config, None, None, hospital_id, "invalid")

        token_id = payload["jti"]
        token_rows = supabase_select(
            config,
            "qr_access_tokens",
            {"id": f"eq.{token_id}", "token_hash": f"eq.{sha256_text(signed_token)}"},
        )
        if not token_rows:
            return reject_scan(config, token_id, None, hospital_id, "invalid")

        token_row = token_rows[0]
        if not token_row["active"] or token_row.get("consumed_at"):
            return reject_scan(config, token_id, None, hospital_id, "reused")

        attempts = supabase_select(
            config,
            "qr_scan_audit_logs",
            {"token_id": f"eq.{token_id}", "select": "id"},
        )
        if len(attempts) >= MAX_VALIDATION_ATTEMPTS:
            return reject_scan(config, token_id, None, hospital_id, "invalid")

        device_rows = supabase_select(
            config,
            "hospital_devices",
            {
                "device_fingerprint": f"eq.{device_fingerprint}",
                "hospital_id": f"eq.{hospital_id}",
                "recognized": "eq.true",
            },
        )
        if not device_rows:
            return reject_scan(config, token_id, None, hospital_id, "unauthenticated_device")

        request_id = str(uuid4())
        approval_expires_at = datetime.now(timezone.utc) + timedelta(seconds=APPROVAL_TTL_SECONDS)
        device = device_rows[0]

        supabase_patch(
            config,
            "qr_access_tokens",
            {"id": f"eq.{token_id}"},
            {"active": False, "consumed_at": datetime.now(timezone.utc).isoformat()},
        )
        supabase_insert(
            config,
            "qr_access_requests",
            {
                "id": request_id,
                "token_id": token_id,
                "patient_id": token_row["patient_id"],
                "doctor_id": doctor_id,
                "hospital_id": hospital_id,
                "doctor_device_id": device["id"],
                "doctor_device_info": device_info,
                "result": "pending",
                "expires_at": approval_expires_at.isoformat(),
            },
        )
        log_scan(config, device["id"], hospital_id, token_id, "pending")

        # Production should trigger FCM/APNs here for the patient's device.
        return jsonify({"accepted": True, "requestId": request_id, "result": "pending"})

    @app.get("/api/patient/approval-status")
    def get_approval_status():
        patient_id = resolve_patient_id_from_session()
        token_id = request.args.get("tokenId")

        filters = {"patient_id": f"eq.{patient_id}", "order": "requested_at.desc", "limit": "1"}
        if token_id:
            filters["token_id"] = f"eq.{token_id}"

        requests_rows = supabase_select(config, "qr_access_requests", filters)
        return jsonify({"request": to_client_request(requests_rows[0]) if requests_rows else None})

    @app.post("/api/patient/approval-status")
    def submit_approval_status():
        patient_id = resolve_patient_id_from_session()
        body = request.get_json(force=True)
        request_id = required(body, "requestId")
        approved = bool(body.get("approved", False))
        result = "approved" if approved else "denied"

        rows = supabase_select(
            config,
            "qr_access_requests",
            {"id": f"eq.{request_id}", "patient_id": f"eq.{patient_id}", "limit": "1"},
        )
        if not rows:
            return jsonify({"error": "approval request not found"}), 404

        approval = rows[0]
        if approval["result"] != "pending":
            return jsonify({"result": approval["result"]})

        supabase_patch(
            config,
            "qr_access_requests",
            {"id": f"eq.{request_id}", "patient_id": f"eq.{patient_id}"},
            {"result": result, "responded_at": datetime.now(timezone.utc).isoformat()},
        )
        log_scan(config, approval["doctor_device_id"], approval["hospital_id"], approval["token_id"], result)

        return jsonify({"result": result})

    @app.get("/api/patient/audit-log")
    def get_patient_audit_log():
        patient_id = resolve_patient_id_from_session()
        token_rows = supabase_select(
            config,
            "qr_access_tokens",
            {"patient_id": f"eq.{patient_id}", "select": "id"},
        )
        token_ids = [row["id"] for row in token_rows]
        if not token_ids:
            return jsonify({"logs": []})

        logs = supabase_select(
            config,
            "qr_scan_audit_logs",
            {"token_id": f"in.({','.join(token_ids)})", "order": "attempted_at.desc", "limit": "25"},
        )
        return jsonify({"logs": logs})

    @app.post("/api/patient/persona/inquiry")
    def create_persona_inquiry():
        require_persona_config(config)
        patient_id = resolve_patient_id_from_session()
        body = request.get_json(silent=True) or {}
        fields = body.get("fields", {})
        reference_id = f"healthbridge-patient-{sha256_text(patient_id)[:24]}"

        payload = {
            "data": {
                "attributes": {
                    "inquiry-template-id": config.persona_inquiry_template_id,
                    "reference-id": reference_id,
                    "fields": sanitize_persona_fields(fields),
                }
            }
        }
        response = persona_post(config, "/inquiries", payload)
        inquiry = response["data"]
        attributes = inquiry.get("attributes", {})
        meta = response.get("meta", {})

        upsert_patient_persona_identity(
            config=config,
            patient_id=patient_id,
            inquiry_id=inquiry["id"],
            status=attributes.get("status", "created"),
        )

        return jsonify(
            {
                "inquiryId": inquiry["id"],
                "status": attributes.get("status"),
                "sessionToken": meta.get("session-token"),
                "oneTimeLink": meta.get("one-time-link"),
                "oneTimeLinkShort": meta.get("one-time-link-short"),
            }
        )

    @app.get("/api/patient/persona/inquiry/<inquiry_id>")
    def get_persona_inquiry(inquiry_id):
        require_persona_config(config)
        patient_id = resolve_patient_id_from_session()
        response = persona_get(config, f"/inquiries/{inquiry_id}")
        inquiry = response["data"]
        attributes = inquiry.get("attributes", {})

        upsert_patient_persona_identity(
            config=config,
            patient_id=patient_id,
            inquiry_id=inquiry["id"],
            status=attributes.get("status", "unknown"),
        )

        return jsonify(
            {
                "inquiryId": inquiry["id"],
                "status": attributes.get("status"),
                "createdAt": attributes.get("created-at"),
                "completedAt": attributes.get("completed-at"),
            }
        )

    @app.post("/api/patient/ai/message")
    def ask_patient_ai():
        require_gemini_config(config)
        patient_id = resolve_patient_id_from_session()
        body = request.get_json(force=True)
        message = required(body, "message")

        if len(message) > 1000:
            return jsonify({"error": "message is too long"}), 400

        patient_context = load_patient_ai_context(config, patient_id)
        flagged = is_flagged_medical_question(message)
        answer = gemini_generate_health_answer(
            config=config,
            patient_context=patient_context,
            message=message,
        )
        if flagged:
            send_flag_alert(config, patient_id, message)

        return jsonify({"answer": answer, "agent": "Nora", "flagged": flagged})

    return app


class BackendConfig:
    def __init__(
        self,
        supabase_url,
        service_role_key,
        private_key_pem,
        public_key_pem,
        persona_api_key,
        persona_inquiry_template_id,
        gemini_api_key,
        gemini_model,
        smtp_host,
        smtp_port,
        smtp_username,
        smtp_password,
        smtp_from_email,
    ):
        self.supabase_url = supabase_url.rstrip("/")
        self.service_role_key = service_role_key
        self.private_key_pem = private_key_pem.replace("\\n", "\n") if private_key_pem else None
        self.public_key_pem = public_key_pem.replace("\\n", "\n") if public_key_pem else None
        self.persona_api_key = persona_api_key
        self.persona_inquiry_template_id = persona_inquiry_template_id
        self.gemini_api_key = gemini_api_key
        self.gemini_model = gemini_model or DEFAULT_GEMINI_MODEL
        self.smtp_host = smtp_host
        self.smtp_port = int(smtp_port or 587)
        self.smtp_username = smtp_username
        self.smtp_password = smtp_password
        self.smtp_from_email = smtp_from_email or smtp_username

    @classmethod
    def from_env(cls):
        return cls(
            supabase_url=required_env("SUPABASE_URL"),
            service_role_key=required_env("SUPABASE_SERVICE_ROLE_KEY"),
            private_key_pem=os.environ.get("QR_TOKEN_PRIVATE_KEY_PEM"),
            public_key_pem=os.environ.get("QR_TOKEN_PUBLIC_KEY_PEM"),
            persona_api_key=os.environ.get("PERSONA_API_KEY"),
            persona_inquiry_template_id=os.environ.get("PERSONA_INQUIRY_TEMPLATE_ID"),
            gemini_api_key=os.environ.get("GEMINI_API_KEY"),
            gemini_model=os.environ.get("GEMINI_MODEL"),
            smtp_host=os.environ.get("SMTP_HOST"),
            smtp_port=os.environ.get("SMTP_PORT"),
            smtp_username=os.environ.get("SMTP_USERNAME"),
            smtp_password=os.environ.get("SMTP_PASSWORD"),
            smtp_from_email=os.environ.get("SMTP_FROM_EMAIL"),
        )


def resolve_patient_id_from_session():
    # Replace this stub with Supabase JWT verification and auth.uid lookup.
    # The mobile client must not send a raw patient UUID; the backend derives it from auth.
    opaque_session = request.headers.get("X-Patient-Session")
    if not opaque_session:
        raise ValueError("missing patient session")
    return required_env("DEMO_PATIENT_ID")


def resolve_doctor_id_from_session():
    # Replace this stub with Supabase JWT verification for authenticated hospital accounts.
    doctor_id = request.headers.get("X-Doctor-Account-Id")
    if not doctor_id:
        raise ValueError("missing doctor account")
    return doctor_id


def supabase_headers(config):
    return {
        "apikey": config.service_role_key,
        "Authorization": f"Bearer {config.service_role_key}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }


def supabase_select(config, table, filters):
    response = requests.get(
        f"{config.supabase_url}/rest/v1/{table}",
        headers=supabase_headers(config),
        params=filters,
        timeout=8,
    )
    response.raise_for_status()
    return response.json()


def supabase_insert(config, table, payload):
    response = requests.post(
        f"{config.supabase_url}/rest/v1/{table}",
        headers=supabase_headers(config),
        json=payload,
        timeout=8,
    )
    response.raise_for_status()
    return response.json()


def supabase_patch(config, table, filters, payload):
    response = requests.patch(
        f"{config.supabase_url}/rest/v1/{table}",
        headers=supabase_headers(config),
        params=filters,
        json=payload,
        timeout=8,
    )
    response.raise_for_status()
    return response.json()


def supabase_upsert(config, table, payload, on_conflict):
    headers = {
        **supabase_headers(config),
        "Prefer": "resolution=merge-duplicates,return=representation",
    }
    response = requests.post(
        f"{config.supabase_url}/rest/v1/{table}",
        headers=headers,
        params={"on_conflict": on_conflict},
        json=payload,
        timeout=8,
    )
    response.raise_for_status()
    return response.json()


def persona_headers(config):
    return {
        "Authorization": f"Bearer {config.persona_api_key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Persona-Version": PERSONA_VERSION,
    }


def persona_post(config, path, payload):
    response = requests.post(
        f"{PERSONA_API_BASE_URL}{path}",
        headers=persona_headers(config),
        json=payload,
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def persona_get(config, path):
    response = requests.get(
        f"{PERSONA_API_BASE_URL}{path}",
        headers=persona_headers(config),
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def require_qr_config(config):
    if not config.private_key_pem or not config.public_key_pem:
        raise RuntimeError("QR_TOKEN_PRIVATE_KEY_PEM and QR_TOKEN_PUBLIC_KEY_PEM are required")


def require_persona_config(config):
    if not config.persona_api_key or not config.persona_inquiry_template_id:
        raise RuntimeError("PERSONA_API_KEY and PERSONA_INQUIRY_TEMPLATE_ID are required")


def require_gemini_config(config):
    if not config.gemini_api_key:
        raise RuntimeError("GEMINI_API_KEY is required")


def gemini_generate_health_answer(config, patient_context, message):
    payload = {
        "systemInstruction": {
            "parts": [
                {
                    "text": NORA_SYSTEM_INSTRUCTIONS
                }
            ]
        },
        "contents": [
            {
                "role": "user",
                "parts": [
                    {
                        "text": (
                            f"Patient record context:\n{patient_context}\n\n"
                            f"Patient question:\n{message}"
                        )
                    }
                ],
            }
        ],
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 450,
        },
    }
    response = requests.post(
        f"{GEMINI_API_BASE_URL}/models/{config.gemini_model}:generateContent",
        params={"key": config.gemini_api_key},
        headers={"Content-Type": "application/json"},
        json=payload,
        timeout=20,
    )
    response.raise_for_status()
    data = response.json()
    candidates = data.get("candidates", [])
    if not candidates:
        return "I could not create an answer right now. Please try again."

    parts = candidates[0].get("content", {}).get("parts", [])
    answer = "\n".join(part.get("text", "") for part in parts).strip()
    return answer or "I could not create an answer right now. Please try again."


def is_flagged_medical_question(message):
    normalized = message.lower()
    urgent_terms = [
        "chest pain",
        "can't breathe",
        "cannot breathe",
        "shortness of breath",
        "stroke",
        "suicide",
        "overdose",
        "severe bleeding",
        "emergency",
    ]
    professional_terms = [
        "diagnose",
        "diagnosis",
        "dosage",
        "dose",
        "prescribe",
        "stop taking",
        "change my medication",
    ]
    return any(term in normalized for term in urgent_terms + professional_terms)


def send_flag_alert(config, patient_id, message):
    if not config.smtp_host or not config.smtp_from_email:
        print("Nora flag alert skipped because SMTP is not configured")
        return

    email = EmailMessage()
    email["Subject"] = "Nora flagged medical question"
    email["From"] = config.smtp_from_email
    email["To"] = FLAG_ALERT_RECIPIENT
    email.set_content(
        "\n".join(
            [
                "Nora flagged a question that may require a licensed professional.",
                f"Patient ID: {patient_id}",
                f"Question: {message}",
            ]
        )
    )

    try:
        with smtplib.SMTP(config.smtp_host, config.smtp_port, timeout=10) as smtp:
            smtp.starttls()
            if config.smtp_username and config.smtp_password:
                smtp.login(config.smtp_username, config.smtp_password)
            smtp.send_message(email)
    except Exception as exc:
        print(f"Nora flag alert failed: {exc}")


def load_patient_ai_context(config, patient_record_id):
    record_rows = supabase_select(
        config,
        "patient_health_records",
        {"patient_record_id": f"eq.{patient_record_id}", "limit": "1"},
    )
    medications = supabase_select(
        config,
        "medications",
        {"patient_record_id": f"eq.{patient_record_id}", "order": "status.asc"},
    )
    allergies_rows = supabase_select(
        config,
        "allergies",
        {"patient_record_id": f"eq.{patient_record_id}", "order": "severity.desc"},
    )
    conditions_rows = supabase_select(
        config,
        "health_conditions",
        {"patient_record_id": f"eq.{patient_record_id}", "order": "condition_name.asc"},
    )
    observations = supabase_select(
        config,
        "health_observations",
        {"patient_record_id": f"eq.{patient_record_id}", "order": "created_at.desc", "limit": "12"},
    )

    return "\n".join(
        [
            summarize_record(record_rows[0] if record_rows else {}),
            summarize_medications(medications),
            summarize_allergies(allergies_rows),
            summarize_conditions(conditions_rows),
            summarize_observations(observations),
        ]
    )


def summarize_record(record):
    if not record:
        return "Patient summary: no profile details found."

    return (
        "Patient summary: "
        f"date of birth {record.get('date_of_birth', 'unknown')}; "
        f"sex at birth {record.get('sex_at_birth', 'unknown')}; "
        f"blood type {record.get('blood_type', 'unknown')}; "
        f"preferred language {record.get('preferred_language', 'unknown')}; "
        f"notes: {record.get('notes') or 'none'}."
    )


def summarize_medications(rows):
    if not rows:
        return "Medications: none listed."

    items = [
        f"{row.get('medication_name')} {row.get('dose')} {row.get('route')} {row.get('frequency')} ({row.get('status')})"
        for row in rows
    ]
    return "Medications: " + "; ".join(items)


def summarize_allergies(rows):
    if not rows:
        return "Allergies: none listed."

    items = [
        f"{row.get('allergen')}: {row.get('reaction')} ({row.get('severity')})"
        for row in rows
    ]
    return "Allergies: " + "; ".join(items)


def summarize_conditions(rows):
    if not rows:
        return "Conditions: none listed."

    items = [
        f"{row.get('condition_name')} ({row.get('status')}, {row.get('severity')}): {row.get('notes') or 'no notes'}"
        for row in rows
    ]
    return "Conditions: " + "; ".join(items)


def summarize_observations(rows):
    if not rows:
        return "Recent observations: none listed."

    items = [
        f"{row.get('observation_type')} {row.get('value_numeric') or row.get('value_text')} {row.get('unit') or ''}"
        for row in rows
    ]
    return "Recent observations: " + "; ".join(items)


def sanitize_persona_fields(fields):
    allowed_fields = {
        "name-first",
        "name-last",
        "birthdate",
        "address-street-1",
        "address-city",
        "address-subdivision",
        "address-postal-code",
    }
    return {
        key: value
        for key, value in fields.items()
        if key in allowed_fields and value is not None and value != ""
    }


def upsert_patient_persona_identity(config, patient_id, inquiry_id, status):
    supabase_upsert(
        config,
        "patient_persona_identities",
        {
            "patient_id": patient_id,
            "persona_inquiry_id": inquiry_id,
            "status": status,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        },
        "patient_id",
    )


def reject_scan(config, token_id, device_id, hospital_id, result):
    log_scan(config, device_id, hospital_id, token_id, result)
    return jsonify({"accepted": False, "result": result}), 400


def log_scan(config, doctor_device_id, hospital_id, token_id, result):
    supabase_insert(
        config,
        "qr_scan_audit_logs",
        {
            "doctor_device_id": doctor_device_id,
            "hospital_id": hospital_id,
            "token_id": token_id,
            "result": result,
        },
    )


def to_client_request(row):
    return {
        "requestId": row["id"],
        "doctorName": "Doctor",
        "hospitalName": "Hospital",
        "deviceInfo": row["doctor_device_info"],
        "tokenId": row["token_id"],
        "requestedAtMillis": millis(parse_supabase_time(row["requested_at"])),
        "result": row["result"],
    }


def sha256_text(value):
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def millis(value):
    return int(value.timestamp() * 1000)


def parse_supabase_time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def required(mapping, key):
    value = mapping.get(key)
    if not value:
        raise ValueError(f"missing {key}")
    return value


def required_env(name):
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def read_upstream_error(response):
    try:
        data = response.json()
    except ValueError:
        return response.text or "Upstream request failed"

    return (
        data.get("error", {}).get("message")
        if isinstance(data.get("error"), dict)
        else data.get("error")
    ) or data.get("message") or response.text or "Upstream request failed"


if __name__ == "__main__":
    create_app().run(debug=True)
