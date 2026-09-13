-- Run after ../database/supabase_qr_access.sql in the Supabase SQL editor.
-- Ask Nora is intentionally patient-owned: no conversation is readable across accounts.

create table if not exists public.ask_nora_sessions (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null default auth.uid() references public.patient_profiles(id) on delete cascade,
  title text,
  initial_mode text not null check (initial_mode in ('text', 'voice')),
  conversation_id text,
  summary text,
  created_at timestamptz not null default now(),
  ended_at timestamptz
);

alter table public.ask_nora_sessions
  add column if not exists conversation_id text;

alter table public.ask_nora_sessions
  add column if not exists summary text;

create table if not exists public.ask_nora_turns (
  id uuid primary key default gen_random_uuid(),
  session_id uuid not null references public.ask_nora_sessions(id) on delete cascade,
  turn_index integer not null check (turn_index >= 0),
  speaker text not null check (speaker in ('user', 'assistant')),
  modality text not null check (modality in ('text', 'voice')),
  text text not null check (char_length(trim(text)) > 0),
  created_at timestamptz not null default now(),
  unique (session_id, turn_index)
);

create index if not exists ask_nora_sessions_patient_created_idx
  on public.ask_nora_sessions(patient_id, created_at desc);
create index if not exists ask_nora_turns_session_index_idx
  on public.ask_nora_turns(session_id, turn_index);

alter table public.ask_nora_sessions enable row level security;
alter table public.ask_nora_turns enable row level security;

create policy "Patients manage their own Ask Nora sessions"
  on public.ask_nora_sessions for all to authenticated
  using (patient_id = auth.uid()) with check (patient_id = auth.uid());

create policy "Patients manage turns in their own Ask Nora sessions"
  on public.ask_nora_turns for all to authenticated
  using (exists (
    select 1 from public.ask_nora_sessions session
    where session.id = ask_nora_turns.session_id and session.patient_id = auth.uid()
  ))
  with check (exists (
    select 1 from public.ask_nora_sessions session
    where session.id = ask_nora_turns.session_id and session.patient_id = auth.uid()
  ));

grant select, insert, update on public.ask_nora_sessions to authenticated;
grant select, insert on public.ask_nora_turns to authenticated;
