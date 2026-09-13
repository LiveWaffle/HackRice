-- Run this in Supabase SQL Editor to match the live database to the app.
-- It fixes:
-- 1. missing public.patient_app_settings in PostgREST schema cache
-- 2. stale get_my_health_record functions that reference observed_at
-- 3. overloaded get_my_health_record RPC ambiguity

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

do $$
declare
  existing_function record;
begin
  for existing_function in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'get_my_health_record'
  loop
    execute format('drop function if exists %s', existing_function.signature);
  end loop;
end $$;

create or replace function public.get_my_health_record()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  current_user_id uuid;
  current_profile public.patient_profiles;
  record_id uuid;
  response jsonb;
begin
  current_user_id := auth.uid();

  if current_user_id is null then
    raise exception 'not authenticated';
  end if;

  select *
  into current_profile
  from public.patient_profiles
  where id = current_user_id;

  if current_profile.id is null then
    return jsonb_build_object(
      'profile', null,
      'health_record', null,
      'active_medications', '[]'::jsonb,
      'past_medications', '[]'::jsonb,
      'allergies', '[]'::jsonb,
      'conditions', '[]'::jsonb,
      'recent_observations', '[]'::jsonb,
      'providers', '[]'::jsonb,
      'access_logs', '[]'::jsonb,
      'audit_entries', '[]'::jsonb,
      'status', 'profile_not_created'
    );
  end if;

  record_id := current_profile.patient_record_id;

  if record_id is null then
    return jsonb_build_object(
      'profile', jsonb_build_object(
        'display_name', current_profile.display_name,
        'phone_number', current_profile.phone_number,
        'emergency_contact', current_profile.emergency_contact
      ),
      'health_record', null,
      'active_medications', '[]'::jsonb,
      'past_medications', '[]'::jsonb,
      'allergies', '[]'::jsonb,
      'conditions', '[]'::jsonb,
      'recent_observations', '[]'::jsonb,
      'providers', '[]'::jsonb,
      'access_logs', '[]'::jsonb,
      'audit_entries', '[]'::jsonb,
      'status', 'record_not_linked'
    );
  end if;

  select jsonb_build_object(
    'profile', jsonb_build_object(
      'display_name', current_profile.display_name,
      'phone_number', current_profile.phone_number,
      'emergency_contact', current_profile.emergency_contact
    ),
    'health_record', coalesce((
      select jsonb_build_object(
        'date_of_birth', record.date_of_birth,
        'sex_at_birth', record.sex_at_birth,
        'blood_type', record.blood_type,
        'height_cm', record.height_cm,
        'weight_kg', record.weight_kg,
        'preferred_language', record.preferred_language,
        'emergency_contact_name', record.emergency_contact_name,
        'emergency_contact_phone', record.emergency_contact_phone,
        'notes', record.notes
      )
      from public.patient_health_records record
      where record.patient_record_id = record_id
    ), 'null'::jsonb),
    'active_medications', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'medication_name', medication.medication_name,
          'dose', medication.dose,
          'route', medication.route,
          'frequency', medication.frequency,
          'start_date', medication.start_date,
          'status', medication.status,
          'notes', medication.notes
        )
        order by medication.start_date desc nulls last, medication.created_at desc
      )
      from public.medications medication
      where medication.patient_record_id = record_id
        and lower(coalesce(medication.status, '')) = 'active'
    ), '[]'::jsonb),
    'past_medications', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'medication_name', medication.medication_name,
          'dose', medication.dose,
          'route', medication.route,
          'frequency', medication.frequency,
          'start_date', medication.start_date,
          'end_date', medication.end_date,
          'status', medication.status,
          'notes', medication.notes
        )
        order by medication.end_date desc nulls last, medication.start_date desc nulls last
      )
      from public.medications medication
      where medication.patient_record_id = record_id
        and lower(coalesce(medication.status, '')) <> 'active'
    ), '[]'::jsonb),
    'allergies', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'allergen', allergy.allergen,
          'reaction', allergy.reaction,
          'severity', allergy.severity,
          'status', allergy.status,
          'first_observed_date', allergy.first_observed_date,
          'notes', allergy.notes
        )
        order by allergy.first_observed_date desc nulls last, allergy.created_at desc
      )
      from public.allergies allergy
      where allergy.patient_record_id = record_id
    ), '[]'::jsonb),
    'conditions', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'condition_name', condition.condition_name,
          'diagnosis_code', condition.diagnosis_code,
          'status', condition.status,
          'severity', condition.severity,
          'diagnosed_date', condition.diagnosed_date,
          'resolved_date', condition.resolved_date,
          'notes', condition.notes
        )
        order by condition.diagnosed_date desc nulls last, condition.created_at desc
      )
      from public.health_conditions condition
      where condition.patient_record_id = record_id
    ), '[]'::jsonb),
    'recent_observations', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'observation_type', observation.observation_type,
          'value_numeric', observation.value_numeric,
          'value_text', observation.value_text,
          'unit', observation.unit,
          'source', observation.source,
          'recorded_at', observation.recorded_at,
          'notes', observation.notes
        )
        order by observation.recorded_at desc
      )
      from (
        select *
        from public.health_observations
        where patient_record_id = record_id
        order by recorded_at desc
        limit 20
      ) observation
    ), '[]'::jsonb),
    'providers', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'first_name', provider.first_name,
          'last_name', provider.last_name,
          'specialty', null,
          'organization_name', null,
          'phone', null,
          'email', null,
          'access_level', access.access_level,
          'expires_at', access.expires_at
        )
        order by access.granted_at desc
      )
      from public.provider_access access
      join public.providers provider
        on provider.provider_id = access.provider_id
      where access.patient_record_id = record_id
        and access.revoked_at is null
        and (access.expires_at is null or access.expires_at > now())
    ), '[]'::jsonb),
    'access_logs', '[]'::jsonb,
    'audit_entries', '[]'::jsonb,
    'status', 'ok'
  )
  into response;

  return response;
end;
$$;

grant execute on function public.get_my_health_record() to authenticated;

notify pgrst, 'reload schema';
