create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to service_role;

create table if not exists private.cat_ai_usage (
  user_id uuid primary key,
  minute_window timestamptz not null,
  minute_count integer not null default 0,
  day_window date not null,
  day_count integer not null default 0
);
alter table private.cat_ai_usage enable row level security;
revoke all on table private.cat_ai_usage from public, anon, authenticated;
grant select, insert, update on table private.cat_ai_usage to service_role;

create or replace function public.consume_cat_ai_quota(p_user_id uuid)
returns table(allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = pg_catalog, private
as $function$
declare
  v_id uuid;
  v_global_id constant uuid := '00000000-0000-0000-0000-000000000000';
  v_minute timestamptz := date_trunc('minute', now());
  v_day date := (now() at time zone 'utc')::date;
  v_minute_count integer;
  v_day_count integer;
  v_allowed boolean := true;
  v_retry integer := 0;
begin
  if p_user_id is null or p_user_id = v_global_id then raise exception 'Invalid user id'; end if;
  foreach v_id in array array[p_user_id, v_global_id] loop
    insert into private.cat_ai_usage(user_id, minute_window, minute_count, day_window, day_count)
    values (v_id, v_minute, 1, v_day, 1)
    on conflict (user_id) do update set
      minute_count = case when private.cat_ai_usage.minute_window = excluded.minute_window then private.cat_ai_usage.minute_count + 1 else 1 end,
      minute_window = excluded.minute_window,
      day_count = case when private.cat_ai_usage.day_window = excluded.day_window then private.cat_ai_usage.day_count + 1 else 1 end,
      day_window = excluded.day_window
    returning minute_count, day_count into v_minute_count, v_day_count;
    if v_id = v_global_id then
      if v_minute_count > 50 then
        v_allowed := false;
        v_retry := greatest(v_retry, greatest(1, ceil(extract(epoch from (v_minute + interval '1 minute' - now())))::integer));
      end if;
      if v_day_count > 500 then
        v_allowed := false;
        v_retry := greatest(v_retry, greatest(1, ceil(extract(epoch from (((v_day + 1)::timestamp at time zone 'utc') - now())))::integer));
      end if;
    else
      if v_minute_count > 8 then
        v_allowed := false;
        v_retry := greatest(v_retry, greatest(1, ceil(extract(epoch from (v_minute + interval '1 minute' - now())))::integer));
      end if;
      if v_day_count > 60 then
        v_allowed := false;
        v_retry := greatest(v_retry, greatest(1, ceil(extract(epoch from (((v_day + 1)::timestamp at time zone 'utc') - now())))::integer));
      end if;
    end if;
  end loop;
  return query select v_allowed, v_retry;
end;
$function$;

revoke all on function public.consume_cat_ai_quota(uuid) from public, anon, authenticated;
grant execute on function public.consume_cat_ai_quota(uuid) to service_role;
