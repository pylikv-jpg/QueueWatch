import test from 'node:test';
import assert from 'node:assert/strict';
import { validate } from './validation.ts';

const now = Date.parse('2026-09-27T18:00:00Z');
const base = {
  event_id:'11111111-1111-4111-8111-111111111111', event_type:'observation',
  install_id:'22222222-2222-4222-8222-222222222222', forecast_session_id:'33333333-3333-4333-8333-333333333333',
  algorithm_version:'v1', checkpoint_id:'98b5be92-d3a5-4ba2-9106-76eb4eb3df49', vehicle_type:'TRUCK', started_at:'2026-09-27T16:00:00Z'
};
const observation = {
  ...base, observed_at:'2026-09-27T17:00:00Z', observation_state:'IN_QUEUE', current_position:90,
  queue_count_same_type:120, data_gap:false, baseline_predicted_minutes:null, baseline_unavailable_reason:'insufficient_speed_data',
  shadow_algorithm_version:'elapsed_window_v2', shadow_predicted_minutes:540, shadow_unavailable_reason:null,
  shadow_speed:10, window_minutes:60, moved_positions:10
};
test('accepts a numeric shadow without a baseline and a warmup without either ETA', () => {
  assert.equal(validate(observation,now),null);
  assert.equal(validate({...observation,shadow_predicted_minutes:null,shadow_unavailable_reason:'collecting_60_minutes',
    shadow_speed:null,window_minutes:0,moved_positions:0},now),null);
});
test('missing/error polls must have no invented position or prediction', () => {
  const missing = {...observation,observation_state:'SOURCE_ERROR',current_position:null,queue_count_same_type:null,data_gap:true,
    baseline_unavailable_reason:'source_error',shadow_predicted_minutes:null,shadow_unavailable_reason:'no_confirmed_position',
    shadow_speed:null,window_minutes:null,moved_positions:null};
  assert.equal(validate(missing,now),null);
  assert.notEqual(validate({...missing,current_position:90},now),null);
  assert.notEqual(validate({...missing,shadow_predicted_minutes:10,shadow_unavailable_reason:null},now),null);
});
test('rejects identifiers, missing keys, invalid numerics and unsupported types', () => {
  for (const extra of ['regnum','plate','android_id']) assert.notEqual(validate({...observation,[extra]:'private'},now),null);
  for (const key of Object.keys(observation)) { const e={...observation}; delete e[key]; assert.notEqual(validate(e,now),null,key); }
  for (const update of [{shadow_speed:Infinity},{shadow_predicted_minutes:NaN},{vehicle_type:'UNKNOWN'},
    {current_position:1},{window_minutes:59},{moved_positions:0},{shadow_algorithm_version:'other'},
    {observed_at:'2026-09-27T15:00:00Z'}]) assert.notEqual(validate({...observation,...update},now),null);
});
test('old v1 prediction and call payloads remain accepted', () => {
  const prediction={...base,event_type:'prediction',observed_at:observation.observed_at,current_position:90,
    queue_count_same_type:120,historical_speed:null,live_speed:60,effective_speed:60,
    predicted_minutes:89,predicted_low_minutes:40,predicted_high_minutes:140,confidence:'LOW',
    live_sample_count:5,historical_sample_count:0,data_gap:false,stale_live_data:false};
  assert.equal(validate(prediction,now),null);
  assert.equal(validate({...base,event_type:'actual_call',called_at:'2026-09-27T18:00:00Z',last_in_queue_at:'2026-09-27T17:59:00Z'},now),null);
});
