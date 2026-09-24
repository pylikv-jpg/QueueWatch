-- Owner/service-role only. Prevent long sessions from dominating by selecting
-- one prediction per session and position band before aggregate comparisons.
with ranked as (
 select *, row_number() over (partition by forecast_session_id,
   case when current_position<=5 then '1-5' when current_position<=20 then '6-20'
        when current_position<=50 then '21-50' when current_position<=100 then '51-100' else '101+' end
   order by observed_at) as n from public.queuewatch_forecast_accuracy
)
select checkpoint_id, vehicle_type, algorithm_version, count(*) as predictions,
 count(distinct forecast_session_id) as completed_sessions,
 percentile_cont(0.5) within group (order by absolute_error_minutes) as median_error_minutes,
 avg(absolute_error_minutes) as mae_minutes, avg(signed_error_minutes) as bias_minutes,
 avg(in_interval::integer) as interval_coverage
from ranked where n=1 group by checkpoint_id,vehicle_type,algorithm_version;
