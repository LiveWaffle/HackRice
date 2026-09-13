-- Pending intake drafts for Ask Nora.
-- These are review-only records that are never merged directly into medications/conditions tables.

create table if not exists public.ask_nora_intake_drafts (
  id uuid primary key default gen_random_uuid(),
  session_id uuid references public.ask_nora_sessions(id) on delete cascade,
  patient_id uuid not null references public.patient_profiles(id) on delete cascade,
  intake_type text not null check (
    intake_type in ('medication', 'symptom', 'test_ordered', 'condition', 'allergy', 'other')
  ),
  raw_statement text not null,
  structured jsonb not null default '{}'::jsonb,
  confidence text not null check (confidence in ('high', 'low')),
  needs_review boolean not null default true,
  source text not null default 'voice_intake',
  timestamp timestamptz not null default now(),
  language text not null default 'en',
  created_at timestamptz not null default now()
);

create index if not exists ask_nora_intake_drafts_patient_created_idx
  on public.ask_nora_intake_drafts(patient_id, created_at desc);

create index if not exists ask_nora_intake_drafts_session_idx
  on public.ask_nora_intake_drafts(session_id);

alter table public.ask_nora_intake_drafts enable row level security;

create policy "Patients manage their own intake drafts"
  on public.ask_nora_intake_drafts for all to authenticated
  using (patient_id = auth.uid())
  with check (patient_id = auth.uid());

grant select, insert, update on public.ask_nora_intake_drafts to authenticated;
