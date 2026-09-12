-- HealthBridge no-history test patient.
--
-- Supabase Auth users should be created through Authentication > Users
-- or through the normal app sign-up flow, not by directly inserting into auth.users.
--
-- Test login to create in Supabase Auth:
--   Email: gilliamandrew22+empty@gmail.com
--   Password: HealthBridgeEmpty1!
--
-- This query creates an app-side patient record with no medications,
-- allergies, conditions, observations, or provider access rows. It also
-- updates ensure_patient_profile so that Auth email maps to this record.

insert into public.patient_health_records (
  patient_record_id,
  date_of_birth,
  sex_at_birth,
  blood_type,
  height_cm,
  weight_kg,
  preferred_language,
  emergency_contact_name,
  emergency_contact_phone,
  notes
) values (
  '33333333-3333-3333-3333-333333333333',
  '1950-01-01',
  'unknown',
  null,
  null,
  null,
  'English',
  'Test Contact',
  '+1-713-555-0000',
  'No medical history has been added for this test patient.'
)
on conflict (patient_record_id) do update
  set preferred_language = excluded.preferred_language,
      emergency_contact_name = excluded.emergency_contact_name,
      emergency_contact_phone = excluded.emergency_contact_phone,
      notes = excluded.notes;

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

grant execute on function public.ensure_patient_profile(text) to authenticated;

notify pgrst, 'reload schema';
