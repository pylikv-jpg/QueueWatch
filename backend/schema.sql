-- Dedicated Forecast telemetry. Existing diagnostics are not modified.
create table public.queuewatch_forecast_sessions (
  forecast_session_id uuid primary key,
  install_id uuid not null,
  checkpoint_id uuid not null,
  vehicle_type text not null check (vehicle_type in ('CAR','TRUCK','BUS','MOTORCYCLE')),
  algorithm_version text not null,
  started_at timestamptz not null,
  actual_called_at timestamptz,
  last_in_queue_at timestamptz,
  actual_call_event_id uuid unique,
  created_at timestamptz not null default now()
);
create table public.queuewatch_forecast_predictions (
  event_id uuid primary key,
  forecast_session_id uuid not null references public.queuewatch_forecast_sessions(forecast_session_id),
  observed_at timestamptz not null,
  current_position integer not null check (current_position > 0),
  queue_count_same_type integer not null check (queue_count_same_type >= 0),
  historical_speed double precision,
  live_speed double precision,
  effective_speed double precision not null check (effective_speed >= 0 and effective_speed <= 200),
  predicted_minutes double precision not null check (predicted_minutes >= 0),
  predicted_low_minutes double precision not null check (predicted_low_minutes >= 0),
  predicted_high_minutes double precision not null,
  confidence text not null check (confidence in ('LOW','MEDIUM','HIGH')),
  live_sample_count integer not null,
  historical_sample_count integer not null,
  data_gap boolean not null,
  stale_live_data boolean not null,
  received_at timestamptz not null default now(),
  check (predicted_low_minutes <= predicted_minutes and predicted_minutes <= predicted_high_minutes)
);
create index queuewatch_forecast_predictions_session_idx on public.queuewatch_forecast_predictions(forecast_session_id);
create index queuewatch_forecast_predictions_received_idx on public.queuewatch_forecast_predictions(received_at);
create index queuewatch_forecast_sessions_install_idx on public.queuewatch_forecast_sessions(install_id);
alter table public.queuewatch_forecast_sessions enable row level security;
alter table public.queuewatch_forecast_predictions enable row level security;
revoke all on public.queuewatch_forecast_sessions, public.queuewatch_forecast_predictions from public, anon, authenticated;
grant all on public.queuewatch_forecast_sessions, public.queuewatch_forecast_predictions to service_role;

-- Atomic idempotence, immutable identity and rate limiting. Only the Edge Function may call this.
create function public.ingest_queuewatch_forecast(e jsonb) returns jsonb
language plpgsql security invoker set search_path = '' as $$
declare
  sid uuid := (e->>'forecast_session_id')::uuid;
  iid uuid := (e->>'install_id')::uuid;
  eid uuid := (e->>'event_id')::uuid;
  s public.queuewatch_forecast_sessions;
  old_sid uuid;
  recent_count integer;
  call_time timestamptz;
begin
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(iid::text, 0));
  select * into s from public.queuewatch_forecast_sessions where forecast_session_id = sid for update;
  if found and (s.install_id <> iid or s.checkpoint_id <> (e->>'checkpoint_id')::uuid
    or s.vehicle_type <> e->>'vehicle_type' or s.algorithm_version <> e->>'algorithm_version') then
    return '{"ok":false,"status":409,"error":"session_identity_conflict"}'::jsonb;
  end if;
  select forecast_session_id into old_sid from public.queuewatch_forecast_predictions where event_id = eid;
  if found then
    if old_sid = sid and e->>'event_type' = 'prediction' then
      return '{"ok":true,"duplicate":true}'::jsonb;
    end if;
    return '{"ok":false,"status":409,"error":"event_conflict"}'::jsonb;
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
    if exists(select 1 from public.queuewatch_forecast_predictions where forecast_session_id=sid and observed_at>call_time) then
      return '{"ok":false,"status":400,"error":"call_before_prediction"}'::jsonb;
    end if;
    update public.queuewatch_forecast_sessions set actual_called_at=call_time,
      last_in_queue_at=(e->>'last_in_queue_at')::timestamptz, actual_call_event_id=eid where forecast_session_id=sid;
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
end $$;
revoke all on function public.ingest_queuewatch_forecast(jsonb) from public, anon, authenticated;
grant execute on function public.ingest_queuewatch_forecast(jsonb) to service_role;

-- A call first observed after a long offline gap has no precise ground truth.
create view public.queuewatch_forecast_accuracy with (security_invoker=true) as
select p.*, s.checkpoint_id,s.vehicle_type,s.algorithm_version,
  extract(epoch from (s.actual_called_at-p.observed_at))/60.0 as actual_minutes,
  p.predicted_minutes-extract(epoch from (s.actual_called_at-p.observed_at))/60.0 as signed_error_minutes,
  abs(p.predicted_minutes-extract(epoch from (s.actual_called_at-p.observed_at))/60.0) as absolute_error_minutes,
  (extract(epoch from (s.actual_called_at-p.observed_at))/60.0 between p.predicted_low_minutes and p.predicted_high_minutes) as in_interval
from public.queuewatch_forecast_predictions p join public.queuewatch_forecast_sessions s using(forecast_session_id)
where s.actual_called_at >= p.observed_at and s.last_in_queue_at is not null
  and s.actual_called_at-s.last_in_queue_at between interval '0 seconds' and interval '2 minutes';
revoke all on public.queuewatch_forecast_accuracy from public, anon, authenticated;
grant select on public.queuewatch_forecast_accuracy to service_role;
