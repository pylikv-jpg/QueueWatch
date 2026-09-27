-- Pair estimates on the SAME observation, excluding uncertain call times.
-- Sample once per 30-minute bin so bursts do not dominate the comparison.
with paired as (
  select *, row_number() over (
    partition by forecast_session_id, floor(extract(epoch from observed_at)/1800)
    order by observed_at
  ) as bin_rank
  from public.queuewatch_shadow_accuracy
  where not uncertain_call_time and observation_state='IN_QUEUE'
    and baseline_predicted_minutes is not null and shadow_predicted_minutes is not null
), per_visit as (
  select checkpoint_id, vehicle_type, forecast_session_id,
    avg(abs(baseline_error_minutes)) as baseline_mae,
    avg(abs(shadow_error_minutes)) as shadow_mae,
    count(*) as paired_observations
  from paired where bin_rank=1
  group by checkpoint_id,vehicle_type,forecast_session_id
)
select checkpoint_id,vehicle_type,count(*) as completed_visits,
  sum(paired_observations) as paired_observations,
  avg(baseline_mae) as baseline_visit_mae,avg(shadow_mae) as shadow_visit_mae
from per_visit group by checkpoint_id,vehicle_type;

-- Coverage and source failures must be reported alongside accuracy.
select s.checkpoint_id,s.vehicle_type,o.observation_state,
  count(*) as observations,
  count(*) filter (where baseline_predicted_minutes is not null) as baseline_available,
  count(*) filter (where shadow_predicted_minutes is not null) as shadow_available
from public.queuewatch_forecast_observations o
join public.queuewatch_forecast_sessions s using(forecast_session_id)
group by s.checkpoint_id,s.vehicle_type,o.observation_state;
