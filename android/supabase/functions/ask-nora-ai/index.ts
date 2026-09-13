import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.49.4'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Content-Type': 'application/json',
}

const SYSTEM_INSTRUCTIONS = `Nora is a medical assistant chatbot. Nora answers questions about health, symptoms, treatments, medications, and patient records only.

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
`.trim()

const URGENT_TERMS = [
  'chest pain',
  "can't breathe",
  'cannot breathe',
  'shortness of breath',
  'stroke',
  'suicide',
  'overdose',
  'severe bleeding',
  'emergency',
]

const PROFESSIONAL_TERMS = [
  'diagnose',
  'diagnosis',
  'dosage',
  'dose',
  'prescribe',
  'stop taking',
  'change my medication',
]

function isFlaggedMedicalQuestion(message: string) {
  const normalized = message.toLowerCase()
  return [...URGENT_TERMS, ...PROFESSIONAL_TERMS].some((term) => normalized.includes(term))
}

function buildSummary(record: Record<string, any> | null, medications: any[], allergies: any[], conditions: any[], observations: any[]) {
  const sections = [
    record ? `Patient summary: date of birth ${record.date_of_birth ?? 'unknown'}; sex at birth ${record.sex_at_birth ?? 'unknown'}; blood type ${record.blood_type ?? 'unknown'}; preferred language ${record.preferred_language ?? 'unknown'}; notes: ${record.notes ?? 'none'}.` : 'Patient summary: no profile details found.',
    medications.length ? `Medications: ${medications.map((row) => `${row.medication_name ?? 'Medication'} ${row.dose ?? ''} ${row.route ?? ''} ${row.frequency ?? ''} (${row.status ?? 'unknown'})`).join('; ')}` : 'Medications: none listed.',
    allergies.length ? `Allergies: ${allergies.map((row) => `${row.allergen ?? 'Allergen'}: ${row.reaction ?? 'unknown'} (${row.severity ?? 'unknown'})`).join('; ')}` : 'Allergies: none listed.',
    conditions.length ? `Conditions: ${conditions.map((row) => `${row.condition_name ?? 'Condition'} (${row.status ?? 'unknown'}, ${row.severity ?? 'unknown'}): ${row.notes ?? 'no notes'}`).join('; ')}` : 'Conditions: none listed.',
    observations.length ? `Recent observations: ${observations.map((row) => `${row.observation_type ?? 'Observation'} ${row.value_numeric ?? row.value_text ?? ''} ${row.unit ?? ''}`.trim()).join('; ')}` : 'Recent observations: none listed.',
  ]
  return sections.join('\n')
}

async function loadPatientContext(supabaseUrl: string, serviceRoleKey: string, authToken: string | null) {
  if (!authToken) return 'Patient context unavailable.'

  try {
    const userClient = createClient(supabaseUrl, Deno.env.get('SUPABASE_ANON_KEY') ?? Deno.env.get('SUPABASE_PUBLISHABLE_KEY') ?? '', {
      global: {
        headers: { Authorization: `Bearer ${authToken}` },
      },
    })
    const { data: userData, error: userError } = await userClient.auth.getUser()
    if (userError || !userData.user) {
      return 'Patient context unavailable.'
    }

    const patientId = userData.user.id
    const serviceSupabase = createClient(supabaseUrl, serviceRoleKey, { auth: { persistSession: false, autoRefreshToken: false } })

    const { data: profileRow } = await serviceSupabase
      .from('patient_profiles')
      .select('patient_record_id')
      .eq('id', patientId)
      .maybeSingle()

    const patientRecordId = profileRow?.patient_record_id ?? null
    if (!patientRecordId) {
      return 'Patient context unavailable.'
    }

    const [{ data: record }, { data: medications }, { data: allergies }, { data: conditions }, { data: observations }] = await Promise.all([
      serviceSupabase.from('patient_health_records').select('*').eq('patient_record_id', patientRecordId).maybeSingle(),
      serviceSupabase.from('medications').select('*').eq('patient_record_id', patientRecordId).order('status', { ascending: true }),
      serviceSupabase.from('allergies').select('*').eq('patient_record_id', patientRecordId).order('severity', { ascending: false }),
      serviceSupabase.from('health_conditions').select('*').eq('patient_record_id', patientRecordId).order('condition_name', { ascending: true }),
      serviceSupabase.from('health_observations').select('*').eq('patient_record_id', patientRecordId).order('recorded_at', { ascending: false }).limit(12),
    ])

    return buildSummary(record ?? null, medications ?? [], allergies ?? [], conditions ?? [], observations ?? [])
  } catch {
    return 'Patient context unavailable.'
  }
}

async function generateGeminiText({ geminiKey, model, prompt }: { geminiKey: string; model: string; prompt: string }) {
  const upstream = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${encodeURIComponent(geminiKey)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: SYSTEM_INSTRUCTIONS }] },
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
      generationConfig: {
        temperature: 0.2,
        maxOutputTokens: 2048,
      },
    }),
  })

  const payload = await upstream.json()
  if (!upstream.ok) {
    const errorText = payload?.error?.message ?? 'Gemini request failed.'
    throw new Error(errorText)
  }

  const candidates = Array.isArray(payload.candidates) ? payload.candidates : []
  const text = candidates
    .flatMap((candidate: any) => candidate?.content?.parts ?? [])
    .map((part: any) => part?.text ?? '')
    .join('\n')
    .trim()

  return text || 'I could not create an answer right now. Please try again.'
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')
    const geminiKey = Deno.env.get('GEMINI_API_KEY')
    const geminiModel = Deno.env.get('GEMINI_MODEL') ?? 'gemini-2.5-flash'

    if (!supabaseUrl || !serviceRoleKey) {
      return new Response(JSON.stringify({ error: 'Supabase env vars are not configured.' }), { status: 500, headers: corsHeaders })
    }
    if (!geminiKey) {
      return new Response(JSON.stringify({ error: 'GEMINI_API_KEY is not configured.' }), { status: 500, headers: corsHeaders })
    }

    const body = await req.json().catch(() => ({}))
    const mode = String(body.mode ?? 'message').toLowerCase()
    const message = String(body.message ?? '').trim()
    const transcript = String(body.transcript ?? '').trim()
    const authHeader = req.headers.get('Authorization') ?? ''
    const authToken = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : ''

    if (mode === 'summary' && !transcript) {
      return new Response(JSON.stringify({ answer: 'No conversation details available yet.', flagged: false }), { status: 200, headers: corsHeaders })
    }

    if (mode !== 'summary' && !message) {
      return new Response(JSON.stringify({ error: 'message is required.' }), { status: 400, headers: corsHeaders })
    }

    const patientContext = await loadPatientContext(supabaseUrl, serviceRoleKey, authToken)
    const promptText = mode === 'summary'
      ? `Summarize this conversation in 2-4 sentences for a clinician-friendly overview. Keep it concise and factual.\n\n${transcript}`
      : `Patient record context:\n${patientContext}\n\nPatient question:\n${message}`

    const flagged = mode !== 'summary' ? isFlaggedMedicalQuestion(message) : false
    const responseText = await generateGeminiText({ geminiKey, model: geminiModel, prompt: promptText })

    return new Response(JSON.stringify({
      answer: responseText,
      flagged,
      suggestions: [
        'I have a new symptom to report',
        'I want to ask about a medication',
        'I have a question about a recent visit',
        'Just checking in',
      ],
    }), { status: 200, headers: corsHeaders })
  } catch (error) {
    return new Response(JSON.stringify({
      error: error instanceof Error ? error.message : 'Unknown error',
    }), { status: 500, headers: corsHeaders })
  }
})
