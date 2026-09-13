-- Fix Supabase/PostgREST error:
-- "Could not choose the best candidate function between ..."
--
-- The app calls public.get_my_health_record with no arguments. If an older
-- version of the function still exists with optional arguments, PostgREST can
-- treat both as valid candidates and refuse the RPC call.

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

do $$
declare
  overload record;
begin
  for overload in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'get_my_presage_vitals'
      and p.pronargs > 0
  loop
    execute format('drop function if exists %s', overload.signature);
  end loop;
end $$;

notify pgrst, 'reload schema';

select
  n.nspname as schema_name,
  p.proname as function_name,
  oidvectortypes(p.proargtypes) as argument_types
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in ('get_my_health_record', 'get_my_presage_vitals')
order by p.proname, argument_types;
