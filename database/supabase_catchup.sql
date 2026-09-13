-- Run this after your base schema if the app says it cannot find data/columns.

do $$
declare
  overload record;
begin
  for overload in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'get_my_health_record'
      and p.pronargs > 0
  loop
    execute format('drop function if exists %s', overload.signature);
  end loop;
end $$;

alter table if exists public.ask_nora_sessions
  add column if not exists conversation_id text;

alter table if exists public.ask_nora_sessions
  add column if not exists summary text;

create table if not exists public.ask_nora_intake_drafts (
  id uuid primary key default gen_random_uuid(),
  session_id uuid references public.ask_nora_sessions(id) on delete cascade,
  patient_id uuid not null references public.patient_profiles(id) on delete cascade default auth.uid(),
  intake_type text not null default 'other',
  raw_statement text not null default '',
  structured jsonb not null default '{}'::jsonb,
  confidence text not null default 'low',
  needs_review boolean not null default true,
  source text not null default 'voice_intake',
  timestamp timestamptz not null default now(),
  language text not null default 'en',
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);

create index if not exists ask_nora_intake_drafts_patient_created_idx
  on public.ask_nora_intake_drafts(patient_id, created_at desc);

create index if not exists ask_nora_intake_drafts_session_idx
  on public.ask_nora_intake_drafts(session_id);

alter table public.ask_nora_intake_drafts enable row level security;

drop policy if exists "Patients manage their own intake drafts" on public.ask_nora_intake_drafts;
create policy "Patients manage their own intake drafts"
  on public.ask_nora_intake_drafts for all to authenticated
  using (patient_id = auth.uid())
  with check (patient_id = auth.uid());

grant select, insert, update on public.ask_nora_intake_drafts to authenticated;

create table if not exists public.patient_app_settings (
  patient_id uuid primary key references public.patient_profiles(id) on delete cascade default auth.uid(),
  voice_language_code text not null default 'en',
  dark_mode boolean not null default false,
  high_contrast_mode boolean not null default false,
  simple_visit_summaries boolean not null default true,
  button_size text not null default 'Large',
  reading_speed text not null default 'Slow',
  always_show_captions boolean not null default true,
  speak_screen_changes boolean not null default false,
  reduce_motion boolean not null default true,
  stronger_touch_feedback boolean not null default true,
  confirm_before_leaving_forms boolean not null default true,
  medicine_reminders boolean not null default true,
  appointment_reminders boolean not null default true,
  doctor_access_alerts boolean not null default true,
  weekly_record_summary boolean not null default false,
  require_approval_every_scan boolean not null default true,
  hide_sensitive_notes boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.patient_app_settings enable row level security;

drop policy if exists "Patients manage their own app settings" on public.patient_app_settings;
create policy "Patients manage their own app settings"
  on public.patient_app_settings
  for all to authenticated
  using (auth.uid() = patient_id)
  with check (auth.uid() = patient_id);

grant select, insert, update on public.patient_app_settings to authenticated;

notify pgrst, 'reload schema';
