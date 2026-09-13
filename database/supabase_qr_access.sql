-- HealthBridge secure QR patient access schema for Supabase.
-- Run this in the Supabase SQL editor.
--
-- Security model:
-- - Raw patient UUIDs stay in Postgres and backend service code.
-- - QR payloads contain a signed opaque token, not a patient UUID.
-- - Doctor scan attempts are checked against authenticated hospital devices.
-- - Patient data unlocks only after an approval request is approved.

create extension if not exists pgcrypto;

do $$
begin
  create type public.qr_approval_result as enum (
    'pending',
    'approved',
    'denied',
    'timed_out',
    'invalid',
    'expired',
    'reused',
    'unauthenticated_device'
  );
exception
  when duplicate_object then null;
end $$;

create table if not exists public.patient_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  phone_number text,
  emergency_contact text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.patient_profiles
  add column if not exists updated_at timestamptz not null default now();

alter table public.patient_profiles
  add column if not exists patient_record_id uuid;

create table if not exists public.patient_health_records (
  patient_record_id uuid primary key default gen_random_uuid(),
  date_of_birth date,
  sex_at_birth text,
  blood_type text,
  height_cm numeric(5,2),
  weight_kg numeric(5,2),
  preferred_language text not null default 'English',
  emergency_contact_name text,
  emergency_contact_phone text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.providers (
  provider_id uuid primary key default gen_random_uuid(),
  first_name text not null,
  last_name text not null,
  specialty text,
  organization_name text,
  npi text,
  phone text,
  email text,
  address text,
  city text,
  state text,
  postal_code text,
  accepting_patients boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.health_conditions (
  condition_id uuid primary key default gen_random_uuid(),
  patient_record_id uuid not null references public.patient_health_records(patient_record_id) on delete cascade,
  condition_name text not null,
  diagnosis_code text,
  status text not null default 'active',
  severity text,
  diagnosed_date date,
  resolved_date date,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists health_conditions_patient_record_id_idx
  on public.health_conditions(patient_record_id);

create table if not exists public.medications (
  medication_id uuid primary key default gen_random_uuid(),
  patient_record_id uuid not null references public.patient_health_records(patient_record_id) on delete cascade,
  medication_name text not null,
  dose text,
  route text,
  frequency text,
  start_date date,
  end_date date,
  status text not null default 'active',
  prescribing_provider_id uuid references public.providers(provider_id) on delete set null,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists medications_patient_record_id_idx
  on public.medications(patient_record_id);

create table if not exists public.allergies (
  allergy_id uuid primary key default gen_random_uuid(),
  patient_record_id uuid not null references public.patient_health_records(patient_record_id) on delete cascade,
  allergen text not null,
  reaction text,
  severity text,
  status text not null default 'active',
  first_observed_date date,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists allergies_patient_record_id_idx
  on public.allergies(patient_record_id);

create table if not exists public.health_observations (
  observation_id uuid primary key default gen_random_uuid(),
  patient_record_id uuid not null references public.patient_health_records(patient_record_id) on delete cascade,
  observation_type text not null,
  value_numeric numeric,
  value_text text,
  unit text,
  source text,
  recorded_at timestamptz not null default now(),
  notes text,
  created_at timestamptz not null default now()
);

create index if not exists health_observations_patient_record_id_recorded_at_idx
  on public.health_observations(patient_record_id, recorded_at desc);

create table if not exists public.provider_access (
  provider_access_id uuid primary key default gen_random_uuid(),
  patient_record_id uuid not null references public.patient_health_records(patient_record_id) on delete cascade,
  provider_id uuid not null references public.providers(provider_id) on delete cascade,
  access_level text not null default 'read_only',
  granted_at timestamptz not null default now(),
  expires_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (patient_record_id, provider_id)
);

create index if not exists provider_access_patient_record_id_idx
  on public.provider_access(patient_record_id);

do $$
begin
  alter table public.patient_profiles
    add constraint patient_profiles_patient_record_id_fkey
    foreign key (patient_record_id)
    references public.patient_health_records(patient_record_id)
    on delete set null;
exception
  when duplicate_object then null;
end $$;

create unique index if not exists patient_profiles_patient_record_id_unique
  on public.patient_profiles(patient_record_id)
  where patient_record_id is not null;

create table if not exists public.hospitals (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.doctor_accounts (
  id uuid primary key references auth.users(id) on delete cascade,
  hospital_id uuid not null references public.hospitals(id) on delete restrict,
  display_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.hospital_devices (
  id uuid primary key default gen_random_uuid(),
  hospital_id uuid not null references public.hospitals(id) on delete cascade,
  device_fingerprint text not null unique,
  display_name text not null,
  recognized boolean not null default true,
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.qr_access_tokens (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.patient_profiles(id) on delete cascade,
  token_hash text not null unique,
  nonce_hash text not null unique,
  issued_at timestamptz not null default now(),
  expires_at timestamptz not null,
  consumed_at timestamptz,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint qr_access_tokens_short_ttl check (expires_at <= issued_at + interval '45 seconds')
);

create unique index if not exists one_active_qr_token_per_patient
  on public.qr_access_tokens(patient_id)
  where active is true;

create table if not exists public.qr_access_requests (
  id uuid primary key default gen_random_uuid(),
  token_id uuid not null references public.qr_access_tokens(id) on delete cascade,
  patient_id uuid not null references public.patient_profiles(id) on delete cascade,
  doctor_id uuid not null references public.doctor_accounts(id) on delete restrict,
  hospital_id uuid not null references public.hospitals(id) on delete restrict,
  doctor_device_id uuid not null references public.hospital_devices(id) on delete restrict,
  doctor_device_info text not null,
  result public.qr_approval_result not null default 'pending',
  requested_at timestamptz not null default now(),
  responded_at timestamptz,
  expires_at timestamptz not null default now() + interval '20 seconds'
);

create table if not exists public.qr_scan_audit_logs (
  id uuid primary key default gen_random_uuid(),
  attempted_at timestamptz not null default now(),
  doctor_device_id uuid,
  hospital_id uuid,
  token_id uuid,
  result public.qr_approval_result not null,
  details jsonb not null default '{}'::jsonb
);

alter table public.qr_scan_audit_logs
  add column if not exists token_id uuid;

do $$
begin
  alter table public.qr_scan_audit_logs
    add constraint qr_scan_audit_logs_token_id_fkey
    foreign key (token_id)
    references public.qr_access_tokens(id)
    on delete set null;
exception
  when duplicate_object then null;
end $$;

create table if not exists public.patient_persona_identities (
  patient_id uuid primary key references public.patient_profiles(id) on delete cascade,
  persona_inquiry_id text not null unique,
  status text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.patient_profiles enable row level security;
alter table public.patient_health_records enable row level security;
alter table public.providers enable row level security;
alter table public.health_conditions enable row level security;
alter table public.medications enable row level security;
alter table public.allergies enable row level security;
alter table public.health_observations enable row level security;
alter table public.provider_access enable row level security;
alter table public.hospitals enable row level security;
alter table public.doctor_accounts enable row level security;
alter table public.hospital_devices enable row level security;
alter table public.qr_access_tokens enable row level security;
alter table public.qr_access_requests enable row level security;
alter table public.qr_scan_audit_logs enable row level security;
alter table public.patient_persona_identities enable row level security;

drop policy if exists "Patients can read their own profile" on public.patient_profiles;
create policy "Patients can read their own profile"
  on public.patient_profiles
  for select
  using (auth.uid() = id);

drop policy if exists "Patients can insert their own profile" on public.patient_profiles;
create policy "Patients can insert their own profile"
  on public.patient_profiles
  for insert
  with check (auth.uid() = id);

drop policy if exists "Patients can update their own profile" on public.patient_profiles;
create policy "Patients can update their own profile"
  on public.patient_profiles
  for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

create or replace function public.ensure_patient_profile(display_name text default 'HealthBridge patient')
returns public.patient_profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  profile public.patient_profiles;
  current_user_id uuid;
  current_user_email text;
  safe_display_name text;
  linked_patient_record_id uuid;
begin
  current_user_id := auth.uid();
  current_user_email := lower(coalesce(auth.jwt() ->> 'email', ''));

  if current_user_id is null then
    raise exception 'not authenticated';
  end if;

  safe_display_name := coalesce(nullif(trim(display_name), ''), 'HealthBridge patient');
  linked_patient_record_id := case
    when current_user_email = 'gilliamandrew22@gmail.com'
      then '11111111-1111-1111-1111-111111111111'::uuid
    when current_user_email = 'gilliamandrew22+empty@gmail.com'
      then '33333333-3333-3333-3333-333333333333'::uuid
    else null
  end;

  insert into public.patient_profiles (id, display_name, patient_record_id)
  values (current_user_id, safe_display_name, linked_patient_record_id)
  on conflict (id) do update
    set display_name = coalesce(nullif(excluded.display_name, ''), public.patient_profiles.display_name),
        patient_record_id = coalesce(excluded.patient_record_id, public.patient_profiles.patient_record_id),
        updated_at = now()
  returning * into profile;

  return profile;
end;
$$;

grant usage on schema public to anon, authenticated;
grant select, insert, update on public.patient_profiles to authenticated;
grant execute on function public.ensure_patient_profile(text) to authenticated;

drop policy if exists "Patients can view their linked health record" on public.patient_health_records;
create policy "Patients can view their linked health record"
  on public.patient_health_records
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = patient_health_records.patient_record_id
    )
  );

drop policy if exists "Patients can view their health conditions" on public.health_conditions;
create policy "Patients can view their health conditions"
  on public.health_conditions
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = health_conditions.patient_record_id
    )
  );

drop policy if exists "Patients can view their medications" on public.medications;
create policy "Patients can view their medications"
  on public.medications
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = medications.patient_record_id
    )
  );

drop policy if exists "Patients can view their allergies" on public.allergies;
create policy "Patients can view their allergies"
  on public.allergies
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = allergies.patient_record_id
    )
  );

drop policy if exists "Patients can view their observations" on public.health_observations;
create policy "Patients can view their observations"
  on public.health_observations
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = health_observations.patient_record_id
    )
  );

drop policy if exists "Patients can view providers with access" on public.provider_access;
create policy "Patients can view providers with access"
  on public.provider_access
  for select
  using (
    exists (
      select 1
      from public.patient_profiles profile
      where profile.id = auth.uid()
        and profile.patient_record_id = provider_access.patient_record_id
    )
  );

drop policy if exists "Patients can view their providers" on public.providers;
create policy "Patients can view their providers"
  on public.providers
  for select
  using (
    exists (
      select 1
      from public.provider_access access
      join public.patient_profiles profile
        on profile.patient_record_id = access.patient_record_id
      where profile.id = auth.uid()
        and access.provider_id = providers.provider_id
        and access.revoked_at is null
        and (access.expires_at is null or access.expires_at > now())
    )
  );

grant select on
  public.patient_health_records,
  public.providers,
  public.health_conditions,
  public.medications,
  public.allergies,
  public.health_observations,
  public.provider_access
to authenticated;

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
          'prescribing_provider', case
            when provider.provider_id is null then null
            else jsonb_build_object(
              'first_name', provider.first_name,
              'last_name', provider.last_name,
              'specialty', provider.specialty,
              'organization_name', provider.organization_name,
              'phone', provider.phone
            )
          end,
          'notes', medication.notes
        )
        order by medication.start_date desc nulls last, medication.created_at desc
      )
      from public.medications medication
      left join public.providers provider
        on provider.provider_id = medication.prescribing_provider_id
      where medication.patient_record_id = record_id
        and lower(medication.status) = 'active'
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
        and lower(medication.status) <> 'active'
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
          'specialty', provider.specialty,
          'organization_name', provider.organization_name,
          'phone', provider.phone,
          'email', provider.email,
          'city', provider.city,
          'state', provider.state,
          'access_level', access.access_level,
          'granted_at', access.granted_at,
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
    'access_logs', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'requested_at', access_request.requested_at,
          'responded_at', access_request.responded_at,
          'result', access_request.result,
          'doctor_name', access_request.doctor_name,
          'hospital_name', access_request.hospital_name,
          'doctor_device_info', access_request.doctor_device_info
        )
        order by access_request.requested_at desc
      )
      from (
        select
          request.requested_at,
          request.responded_at,
          request.result,
          doctor.display_name as doctor_name,
          hospital.name as hospital_name,
          request.doctor_device_info
        from public.qr_access_requests request
        left join public.doctor_accounts doctor
          on doctor.id = request.doctor_id
        left join public.hospitals hospital
          on hospital.id = request.hospital_id
        where request.patient_id = current_user_id
        order by request.requested_at desc
        limit 25
      ) access_request
    ), '[]'::jsonb),
    'audit_entries', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'attempted_at', audit_entry.attempted_at,
          'result', audit_entry.result,
          'hospital_name', audit_entry.hospital_name,
          'doctor_device_name', audit_entry.doctor_device_name,
          'details', audit_entry.details
        )
        order by audit_entry.attempted_at desc
      )
      from (
        select
          audit.attempted_at,
          audit.result,
          hospital.name as hospital_name,
          device.display_name as doctor_device_name,
          audit.details
        from public.qr_scan_audit_logs audit
        join public.qr_access_tokens token
          on token.id = audit.token_id
        left join public.hospitals hospital
          on hospital.id = audit.hospital_id
        left join public.hospital_devices device
          on device.id = audit.doctor_device_id
        where token.patient_id = current_user_id
        order by audit.attempted_at desc
        limit 25
      ) audit_entry
    ), '[]'::jsonb),
    'status', 'ok'
  )
  into response;

  return response;
end;
$$;

grant execute on function public.get_my_health_record() to authenticated;

drop policy if exists "Patients can view their own access requests" on public.qr_access_requests;
create policy "Patients can view their own access requests"
  on public.qr_access_requests
  for select
  using (auth.uid() = patient_id);

drop policy if exists "Patients can view their own QR audit logs" on public.qr_scan_audit_logs;
create policy "Patients can view their own QR audit logs"
  on public.qr_scan_audit_logs
  for select
  using (
    exists (
      select 1
      from public.qr_access_tokens token
      where token.id = qr_scan_audit_logs.token_id
        and token.patient_id = auth.uid()
    )
  );

drop policy if exists "Patients can view their own Persona status" on public.patient_persona_identities;
create policy "Patients can view their own Persona status"
  on public.patient_persona_identities
  for select
  using (auth.uid() = patient_id);

grant select on
  public.qr_access_requests,
  public.qr_scan_audit_logs,
  public.patient_persona_identities
to authenticated;

create or replace function public.expire_pending_qr_requests()
returns void
language sql
security definer
as $$
  update public.qr_access_requests
  set result = 'timed_out',
      responded_at = now()
  where result = 'pending'
    and expires_at <= now();
$$;
