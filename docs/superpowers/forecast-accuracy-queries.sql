-- Private admin queries. No client grants or plate identifiers.
-- A call detected after a long offline gap has an uncertain actual time;
-- use only call intervals <= 2 minutes for calibration and report exclusions.
select count(*) as sessions,
       count(*) filter (where actual_called_at is null) as waiting_for_call,
       count(*) filter (where actual_called_at is not null) as completed,
       count(*) filter (where actual_called_at - last_in_queue_at > interval '2 minutes') as uncertain_call_time
from public.queuewatch_forecast_sessions;

select a.algorithm_version, count(*) as predictions,
       count(distinct a.forecast_session_id) as completed_sessions,
       percentile_cont(0.5) within group (order by a.absolute_error_minutes) as median_error_minutes,
       avg(a.absolute_error_minutes) as mae_minutes,
       percentile_cont(0.5) within group (order by a.signed_error_minutes) as median_bias_minutes,
       avg(a.in_interval::int) as interval_coverage
from public.queuewatch_forecast_accuracy a
join public.queuewatch_forecast_sessions s using (forecast_session_id)
where s.actual_called_at - s.last_in_queue_at <= interval '2 minutes'
group by a.algorithm_version;

select a.algorithm_version, a.checkpoint_id, a.vehicle_type,
       count(*) as predictions, count(distinct a.forecast_session_id) as completed_sessions,
       avg(a.absolute_error_minutes) as mae_minutes, avg(a.in_interval::int) as interval_coverage
from public.queuewatch_forecast_accuracy a
join public.queuewatch_forecast_sessions s using (forecast_session_id)
where s.actual_called_at - s.last_in_queue_at <= interval '2 minutes'
group by a.algorithm_version, a.checkpoint_id, a.vehicle_type
having count(distinct a.forecast_session_id) >= 10;

select a.algorithm_version,
       case when current_position <= 5 then '1-5' when current_position <= 20 then '6-20'
            when current_position <= 50 then '21-50' when current_position <= 100 then '51-100'
            else '101+' end as position_band,
       count(*) as predictions, count(distinct a.forecast_session_id) as completed_sessions,
       percentile_cont(0.5) within group (order by absolute_error_minutes) as median_error_minutes,
       avg(signed_error_minutes) as mean_bias_minutes
from public.queuewatch_forecast_accuracy a
join public.queuewatch_forecast_sessions s using (forecast_session_id)
where s.actual_called_at - s.last_in_queue_at <= interval '2 minutes'
group by a.algorithm_version, position_band;

select a.algorithm_version, confidence, count(*) as predictions,
       count(distinct a.forecast_session_id) as completed_sessions,
       avg(in_interval::int) as interval_coverage, avg(absolute_error_minutes) as mae_minutes
from public.queuewatch_forecast_accuracy a
join public.queuewatch_forecast_sessions s using (forecast_session_id)
where s.actual_called_at - s.last_in_queue_at <= interval '2 minutes'
group by a.algorithm_version, confidence;
