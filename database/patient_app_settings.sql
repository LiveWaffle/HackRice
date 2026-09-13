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
