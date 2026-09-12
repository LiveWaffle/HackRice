create or replace function public.save_presage_vitals(
  p_pulse_rate numeric,
  p_pulse_confidence numeric,
  p_breathing_rate numeric,
  p_breathing_confidence numeric,
  p_captured_at timestamptz,
  p_validation_code text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  current_record_id uuid;
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  select patient_record_id
  into current_record_id
  from public.patient_profiles
  where id = auth.uid();

  if current_record_id is null then
    raise exception 'No health record is linked to this account';
  end if;

  insert into public.health_observations (
    patient_record_id,
    observation_type,
    value_numeric,
    unit,
    source,
    observed_at,
    notes
  )
  values
    (
      current_record_id,
      'pulse_rate',
      p_pulse_rate,
      'bpm',
      'presage',
      p_captured_at,
      jsonb_build_object(
        'confidence', p_pulse_confidence,
        'validation_code', p_validation_code
      )::text
    ),
    (
      current_record_id,
      'breathing_rate',
      p_breathing_rate,
      'breaths/min',
      'presage',
      p_captured_at,
      jsonb_build_object(
        'confidence', p_breathing_confidence,
        'validation_code', p_validation_code
      )::text
    );
end;
$$;

revoke all on function public.save_presage_vitals(
  numeric,
  numeric,
  numeric,
  numeric,
  timestamptz,
  text
) from public;

grant execute on function public.save_presage_vitals(
  numeric,
  numeric,
  numeric,
  numeric,
  timestamptz,
  text
) to authenticated;