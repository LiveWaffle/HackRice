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

alter table public.patient_profiles enable row level security;
alter table public.qr_access_requests enable row level security;
alter table public.qr_scan_audit_logs enable row level security;

create policy "Patients can read their own profile"
  on public.patient_profiles
  for select
  using (auth.uid() = id);

create policy "Patients can insert their own profile"
  on public.patient_profiles
  for insert
  with check (auth.uid() = id);

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

create policy "Patients can view their own access requests"
  on public.qr_access_requests
  for select
  using (auth.uid() = patient_id);

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
