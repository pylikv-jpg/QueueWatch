const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const checkpoints = new Set(['53d94097-2b34-11ec-8467-ac1f6bf889c0','7e46a2d1-ab2f-11ec-bafb-ac1f6bf889c1','a9173a85-3fc0-424c-84f0-defa632481e4','3b797d4d-706a-440f-a1a4-826c191e1e36','ffe81c11-00d6-11e8-a967-b0dd44bde851','b60677d4-8a00-4f93-a781-e129e1692a03','98b5be92-d3a5-4ba2-9106-76eb4eb3df49']);
const base = ['event_id','event_type','install_id','forecast_session_id','algorithm_version','checkpoint_id','vehicle_type','started_at'];
const prediction = ['observed_at','current_position','queue_count_same_type','historical_speed','live_speed','effective_speed','predicted_minutes','predicted_low_minutes','predicted_high_minutes','confidence','live_sample_count','historical_sample_count','data_gap','stale_live_data'];
const observation = ['observed_at','observation_state','current_position','queue_count_same_type','data_gap','baseline_predicted_minutes','baseline_unavailable_reason','shadow_algorithm_version','shadow_predicted_minutes','shadow_unavailable_reason','shadow_speed','window_minutes','moved_positions'];
export function validate(e: any, now = Date.now()): string | null {
  if (!e || typeof e !== 'object' || Array.isArray(e)) return 'object_required';
  if (!['prediction','actual_call','observation'].includes(e.event_type)) return 'event_type';
  const allowed = [...base,...(e.event_type==='prediction'?prediction:e.event_type==='observation'?observation:['called_at','last_in_queue_at'])];
  if (Object.keys(e).some(k=>!allowed.includes(k)) || allowed.some(k=>!(k in e))) return 'unexpected_or_missing_field';
  for (const k of ['event_id','install_id','forecast_session_id']) if (typeof e[k]!=='string' || !uuid.test(e[k])) return 'uuid';
  if (!checkpoints.has(e.checkpoint_id) || !['CAR','TRUCK','BUS','MOTORCYCLE'].includes(e.vehicle_type) || e.algorithm_version!=='v1') return 'identity';
  const time = (v: any) => typeof v==='string' && /^\d{4}-\d\d-\d\dT/.test(v) && Number.isFinite(Date.parse(v)) && Date.parse(v)<=now+300000 && Date.parse(v)>=now-30*86400000;
  if (!time(e.started_at)) return 'started_at';
  if (e.event_type==='actual_call') {
    if (!time(e.called_at) || !time(e.last_in_queue_at) || Date.parse(e.called_at)<Date.parse(e.last_in_queue_at) || Date.parse(e.last_in_queue_at)<Date.parse(e.started_at)) return 'call_time';
    return null;
  }
  if (!time(e.observed_at) || Date.parse(e.observed_at)<Date.parse(e.started_at)) return 'observed_at';
  const number = (k: string,min: number,max: number) => typeof e[k]==='number' && Number.isFinite(e[k]) && e[k]>=min && e[k]<=max;
  if (e.event_type==='observation') {
    if (!['IN_QUEUE','MISSING','UNKNOWN','SOURCE_ERROR'].includes(e.observation_state) || typeof e.data_gap!=='boolean') return 'observation_state';
    if (e.current_position!==null && (!number('current_position',1,1000000)||!Number.isInteger(e.current_position))) return 'current_position';
    if (e.queue_count_same_type!==null && (!number('queue_count_same_type',0,1000000)||!Number.isInteger(e.queue_count_same_type))) return 'queue_count_same_type';
    if ((e.observation_state==='IN_QUEUE') !== (e.current_position!==null)) return 'position_state';
    if (e.observation_state==='IN_QUEUE' && e.queue_count_same_type===null) return 'queue_count_same_type';
    if (e.shadow_algorithm_version!=='elapsed_window_v2') return 'shadow_algorithm_version';
    const reason = (value: any) => typeof value==='string' && /^[a-z0-9_]{1,80}$/.test(value);
    for (const prefix of ['baseline','shadow']) {
      const value = prefix+'_predicted_minutes', why = prefix+'_unavailable_reason';
      if (e[value]===null ? !reason(e[why]) : (!number(value,0,240000000)||e[why]!==null)) return 'prediction_state';
      if (e.observation_state!=='IN_QUEUE' && e[value]!==null) return 'prediction_without_position';
    }
    for (const k of ['window_minutes','moved_positions']) if(e[k]!==null&&!number(k,0,1000000)) return k;
    if(e.moved_positions!==null&&!Number.isInteger(e.moved_positions)) return 'moved_positions';
    if(e.shadow_speed!==null&&!number('shadow_speed',0,200)) return 'shadow_speed';
    if(e.shadow_predicted_minutes!==null && (e.current_position<=1 || !number('window_minutes',60,1000000) ||
      !number('moved_positions',1,1000000)||!number('shadow_speed',Number.MIN_VALUE,200))) return 'shadow_evidence';
    return null;
  }
  for (const k of ['current_position','queue_count_same_type','live_sample_count','historical_sample_count']) if(!number(k,k==='current_position'?1:0,1000000)||!Number.isInteger(e[k])) return k;
  for (const k of ['historical_speed','live_speed']) if(e[k]!==null && !number(k,0.25,200)) return k;
  if (!number('effective_speed',0,200)) return 'effective_speed';
  for (const k of ['predicted_minutes','predicted_low_minutes','predicted_high_minutes']) if(!number(k,0,240000000)) return k;
  if(e.predicted_low_minutes>e.predicted_minutes||e.predicted_minutes>e.predicted_high_minutes) return 'range';
  if(!['LOW','MEDIUM','HIGH'].includes(e.confidence)||typeof e.data_gap!=='boolean'||typeof e.stale_live_data!=='boolean') return 'quality';
  return null;
}
