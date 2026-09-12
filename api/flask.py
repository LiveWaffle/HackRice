import hashlib
import os
import secrets
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import jwt
import requests
from flask import Flask, jsonify, request

TOKEN_TTL_SECONDS = 45
APPROVAL_TTL_SECONDS = 20
MAX_VALIDATION_ATTEMPTS = 5


def create_app():
    app = Flask(__name__)
    config = BackendConfig.from_env()

    @app.get("/api/health")
    def health():
        return jsonify({"status": "ok"})

    @app.post("/api/patient/qr-token")
    def create_patient_qr_token():
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

    return app


class BackendConfig:
    def __init__(self, supabase_url, service_role_key, private_key_pem, public_key_pem):
        self.supabase_url = supabase_url.rstrip("/")
        self.service_role_key = service_role_key
        self.private_key_pem = private_key_pem.replace("\\n", "\n")
        self.public_key_pem = public_key_pem.replace("\\n", "\n")

    @classmethod
    def from_env(cls):
        return cls(
            supabase_url=required_env("SUPABASE_URL"),
            service_role_key=required_env("SUPABASE_SERVICE_ROLE_KEY"),
            private_key_pem=required_env("QR_TOKEN_PRIVATE_KEY_PEM"),
            public_key_pem=required_env("QR_TOKEN_PUBLIC_KEY_PEM"),
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


if __name__ == "__main__":
    create_app().run(debug=True)
