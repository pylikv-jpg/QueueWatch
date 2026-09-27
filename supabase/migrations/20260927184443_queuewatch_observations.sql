-- Additive telemetry; existing prediction and actual-call payloads remain valid.
create table public.queuewatch_forecast_observations (
  event_id uuid primary key,
  forecast_session_id uuid not null references public.queuewatch_forecast_sessions(forecast_session_id),
  observed_at timestamptz not null,
  observation_state text not null check (observation_state in ('IN_QUEUE','MISSING','UNKNOWN','SOURCE_ERROR')),
  current_position integer check (current_position between 1 and 1000000),
  queue_count_same_type integer check (queue_count_same_type between 0 and 1000000),
  data_gap boolean not null,
  baseline_predicted_minutes double precision check (baseline_predicted_minutes between 0 and 240000000),
  baseline_unavailable_reason text,
  shadow_algorithm_version text not null check (shadow_algorithm_version = 'elapsed_window_v2'),
  shadow_predicted_minutes double precision check (shadow_predicted_minutes between 0 and 240000000),
  shadow_unavailable_reason text,
  shadow_speed double precision check (shadow_speed between 0 and 200),
  window_minutes double precision check (window_minutes between 0 and 1000000),
  moved_positions integer check (moved_positions between 0 and 1000000),
  received_at timestamptz not null default now(),
  check ((observation_state = 'IN_QUEUE') = (current_position is not null)),
  check ((baseline_predicted_minutes is null) = (baseline_unavailable_reason is not null)),
  check ((shadow_predicted_minutes is null) = (shadow_unavailable_reason is not null))
);
create index queuewatch_observations_session_time on public.queuewatch_forecast_observations(forecast_session_id, observed_at);
create index queuewatch_observations_received on public.queuewatch_forecast_observations(received_at);
alter table public.queuewatch_forecast_observations enable row level security;
revoke all on public.queuewatch_forecast_observations from public, anon, authenticated;
grant select, insert on public.queuewatch_forecast_observations to service_role;

CREATE OR REPLACE FUNCTION public.ingest_queuewatch_forecast(e jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  sid uuid := (e->>'forecast_session_id')::uuid;
  iid uuid := (e->>'install_id')::uuid;
  eid uuid := (e->>'event_id')::uuid;
  s public.queuewatch_forecast_sessions;
  old_sid uuid;
  recent_count integer;
  call_time timestamptz;
begin
  if e->>'event_type' not in ('prediction','actual_call','observation') then
    return '{"ok":false,"status":400,"error":"event_type"}'::jsonb;
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(iid::text, 0));
  select * into s from public.queuewatch_forecast_sessions where forecast_session_id = sid for update;
  if found and (s.install_id <> iid or s.checkpoint_id <> (e->>'checkpoint_id')::uuid
    or s.vehicle_type <> e->>'vehicle_type' or s.algorithm_version <> e->>'algorithm_version'
    or s.started_at <> (e->>'started_at')::timestamptz) then
    return '{"ok":false,"status":409,"error":"session_identity_conflict"}'::jsonb;
  end if;
  select forecast_session_id into old_sid from public.queuewatch_forecast_predictions where event_id = eid;
  if found then
    if old_sid = sid and e->>'event_type' = 'prediction' then
      return '{"ok":true,"duplicate":true}'::jsonb;
    end if;
    return '{"ok":false,"status":409,"error":"event_conflict"}'::jsonb;
  end if;
  select forecast_session_id into old_sid from public.queuewatch_forecast_observations where event_id = eid;
  if found then
    if old_sid = sid and e->>'event_type' = 'observation' then
      return '{"ok":true,"duplicate":true}'::jsonb;
    end if;
    return '{"ok":false,"status":409,"error":"event_conflict"}'::jsonb;
  end if;
  if e->>'event_type' = 'observation' and (
    select count(*) from public.queuewatch_forecast_observations o
    join public.queuewatch_forecast_sessions x using(forecast_session_id)
    where x.install_id=iid and o.received_at > now()-interval '1 hour'
  ) >= 720 then
    return '{"ok":false,"status":429,"error":"observation_rate_limit"}'::jsonb;
  end if;
  select count(*) into recent_count from public.queuewatch_forecast_predictions p
    join public.queuewatch_forecast_sessions x using(forecast_session_id)
    where x.install_id = iid and p.received_at > now() - interval '1 hour';
  if recent_count >= 240 and e->>'event_type' = 'prediction' then
    return '{"ok":false,"status":429,"error":"rate_limit"}'::jsonb;
  end if;
  if s.forecast_session_id is null then
    if (select count(*) from public.queuewatch_forecast_sessions where install_id=iid and created_at > now()-interval '1 hour') >= 30 then
      return '{"ok":false,"status":429,"error":"session_rate_limit"}'::jsonb;
    end if;
    insert into public.queuewatch_forecast_sessions(forecast_session_id,install_id,checkpoint_id,vehicle_type,algorithm_version,started_at)
      values(sid,iid,(e->>'checkpoint_id')::uuid,e->>'vehicle_type',e->>'algorithm_version',(e->>'started_at')::timestamptz);
  end if;
  if e->>'event_type' = 'actual_call' then
    call_time := (e->>'called_at')::timestamptz;
    if s.actual_call_event_id is not null then return '{"ok":true,"already_closed":true}'::jsonb; end if;
    if exists(select 1 from public.queuewatch_forecast_predictions where forecast_session_id=sid and observed_at>call_time)
      or exists(select 1 from public.queuewatch_forecast_observations where forecast_session_id=sid and observed_at>call_time) then
      return '{"ok":false,"status":400,"error":"call_before_prediction"}'::jsonb;
    end if;
    update public.queuewatch_forecast_sessions set actual_called_at=call_time,
      last_in_queue_at=(e->>'last_in_queue_at')::timestamptz, actual_call_event_id=eid where forecast_session_id=sid;
  elsif e->>'event_type' = 'observation' then
    if s.actual_called_at is not null and (e->>'observed_at')::timestamptz > s.actual_called_at then
      return '{"ok":false,"status":400,"error":"observation_after_call"}'::jsonb;
    end if;
    insert into public.queuewatch_forecast_observations (
      event_id,forecast_session_id,observed_at,observation_state,current_position,queue_count_same_type,
      data_gap,baseline_predicted_minutes,baseline_unavailable_reason,shadow_algorithm_version,
      shadow_predicted_minutes,shadow_unavailable_reason,shadow_speed,window_minutes,moved_positions
    ) values (
      eid,sid,(e->>'observed_at')::timestamptz,e->>'observation_state',(e->>'current_position')::integer,
      (e->>'queue_count_same_type')::integer,(e->>'data_gap')::boolean,
      (e->>'baseline_predicted_minutes')::double precision,e->>'baseline_unavailable_reason',e->>'shadow_algorithm_version',
      (e->>'shadow_predicted_minutes')::double precision,e->>'shadow_unavailable_reason',
      (e->>'shadow_speed')::double precision,(e->>'window_minutes')::double precision,(e->>'moved_positions')::integer
    );
  else
    if s.actual_called_at is not null and (e->>'observed_at')::timestamptz > s.actual_called_at then
      return '{"ok":false,"status":400,"error":"prediction_after_call"}'::jsonb;
    end if;
    insert into public.queuewatch_forecast_predictions values (
      eid,sid,(e->>'observed_at')::timestamptz,(e->>'current_position')::integer,
      (e->>'queue_count_same_type')::integer,(e->>'historical_speed')::double precision,
      (e->>'live_speed')::double precision,(e->>'effective_speed')::double precision,
      (e->>'predicted_minutes')::double precision,(e->>'predicted_low_minutes')::double precision,
      (e->>'predicted_high_minutes')::double precision,e->>'confidence',
      (e->>'live_sample_count')::integer,(e->>'historical_sample_count')::integer,
      (e->>'data_gap')::boolean,(e->>'stale_live_data')::boolean,now()
    );
  end if;
  return '{"ok":true}'::jsonb;
end $function$

;
revoke all on function public.ingest_queuewatch_forecast(jsonb) from public, anon, authenticated;
grant execute on function public.ingest_queuewatch_forecast(jsonb) to service_role;

create view public.queuewatch_shadow_accuracy with (security_invoker=true) as
select o.*, s.checkpoint_id, s.vehicle_type,
  extract(epoch from (s.actual_called_at-o.observed_at))/60.0 as actual_minutes,
  o.baseline_predicted_minutes-extract(epoch from (s.actual_called_at-o.observed_at))/60.0 as baseline_error_minutes,
  o.shadow_predicted_minutes-extract(epoch from (s.actual_called_at-o.observed_at))/60.0 as shadow_error_minutes,
  s.actual_called_at-s.last_in_queue_at > interval '2 minutes' as uncertain_call_time
from public.queuewatch_forecast_observations o
join public.queuewatch_forecast_sessions s using(forecast_session_id)
where s.actual_called_at is not null and o.observed_at <= s.actual_called_at;
revoke all on public.queuewatch_shadow_accuracy from public, anon, authenticated;
grant select on public.queuewatch_shadow_accuracy to service_role;
