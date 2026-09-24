# QueueWatch Forecast Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a central, privacy-minimized telemetry path so all installed QueueWatch Forecast apps can submit predictions and actual call outcomes to Supabase for accuracy measurement and later model correction.

**Architecture:** Reuse the existing active Supabase project `TachoWatch` (`eu-west-2`) but keep QueueWatch data in separate tables and a separate Edge Function. Clients never receive database write privileges or service-role credentials; the APK sends bounded JSON to a JWT-protected Edge Function, which validates and writes using the server-side service role. Android stores a capped local retry queue so telemetry outages cannot affect tracking.

**Tech Stack:** Supabase/PostgreSQL 17, Row Level Security, Supabase Edge Functions/Deno, Android Kotlin/HttpURLConnection or shared HTTP helper, JSON, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-24-queuewatch-forecast-design.md`

## Global Constraints

- Use the existing Supabase project; do not create another paid project.
- Keep existing `tachowatch_diagnostic_reports` and `submit-diagnostic-report` unchanged.
- No raw vehicle registration number is stored or transmitted.
- Client APK must never contain a service-role/secret key.
- All public/exposed tables must have RLS enabled and no direct client insert policy.
- Telemetry is best-effort; tracking and alerts must continue when telemetry fails.
- Each forecast record must include `algorithm_version`.
- One outlier/session must not automatically alter model parameters for all users.
- Telemetry retry storage must be bounded.

## Review Focus

- Privacy leakage: no regnum, MAC, device account ID, or user-entered identity reaches QueueWatch telemetry.
- Duplicate retries: repeated delivery of the same prediction event must be idempotent.
- Actual-call reconciliation: closing a session must correctly attach to earlier predictions without needing the plate number.
- Abuse/rate limiting: one installation cannot flood the database indefinitely.
- Offline failure: telemetry HTTP errors must never propagate into the foreground tracking loop.

---

### Task 1: Verify current Supabase behavior and document the integration contract

**Files:**
- Create: `docs/superpowers/queuewatch-forecast-telemetry-contract.md`

**Interfaces:**
- Consumes current Supabase project state.
- Produces a fixed JSON contract for Android and Edge Function implementation.

- [ ] **Step 1: Check current Supabase changelog/docs**

Before any schema/function change:
- fetch the current Supabase changelog;
- search current docs for Edge Function JWT verification, publishable/anon client authorization, RLS, and server-side service-role usage.

Record any breaking change that affects this plan.

- [ ] **Step 2: Confirm existing project state**

Verify:
- project `whvdyxjopfwgzgqqvzaj` is ACTIVE_HEALTHY;
- existing table `public.tachowatch_diagnostic_reports` remains untouched;
- existing function `submit-diagnostic-report` remains ACTIVE.

- [ ] **Step 3: Write exact event contract**

`prediction` event:

```json
{
  "event_id": "uuid",
  "event_type": "prediction",
  "install_id": "random-install-uuid",
  "forecast_session_id": "uuid",
  "algorithm_version": "v1",
  "checkpoint_id": "98b5be92-d3a5-4ba2-9106-76eb4eb3df49",
  "checkpoint_name": "Козловичи",
  "vehicle_type": "TRUCK",
  "observed_at": "2026-09-24T12:00:00Z",
  "current_position": 120,
  "queue_count_same_type": 260,
  "historical_speed": 18.4,
  "live_speed": 24.0,
  "effective_speed": 20.8,
  "predicted_minutes": 343.0,
  "predicted_low_minutes": 285.0,
  "predicted_high_minutes": 405.0,
  "confidence": "MEDIUM",
  "live_sample_count": 4,
  "historical_sample_count": 72,
  "data_gap": false,
  "stale_live_data": false
}
```

`actual_call` event:

```json
{
  "event_id": "uuid",
  "event_type": "actual_call",
  "install_id": "random-install-uuid",
  "forecast_session_id": "uuid",
  "algorithm_version": "v1",
  "called_at": "2026-09-24T17:43:00Z"
}
```

No additional identifiers are accepted.

- [ ] **Step 4: Commit contract doc**

```bash
git add docs/superpowers/queuewatch-forecast-telemetry-contract.md
git commit -m "docs: define QueueWatch Forecast telemetry contract"
```

---

### Task 2: Create private telemetry tables with RLS

**Files:**
- Supabase schema migration only; no Android file changes.

**Interfaces:**
- Produces:
  - `public.queuewatch_forecast_sessions`
  - `public.queuewatch_forecast_predictions`

- [ ] **Step 1: Create session table**

Use this schema:

```sql
create table public.queuewatch_forecast_sessions (
  id uuid primary key default gen_random_uuid(),
  forecast_session_id uuid not null unique,
  install_id text not null check (char_length(install_id) between 8 and 128),
  algorithm_version text not null check (char_length(algorithm_version) between 1 and 50),
  checkpoint_id text not null check (char_length(checkpoint_id) between 1 and 100),
  checkpoint_name text not null check (char_length(checkpoint_name) between 1 and 100),
  vehicle_type text not null check (vehicle_type in ('CAR','TRUCK','BUS','MOTORCYCLE')),
  first_seen_at timestamptz not null,
  actual_called_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index queuewatch_forecast_sessions_checkpoint_idx
  on public.queuewatch_forecast_sessions(checkpoint_id, vehicle_type, algorithm_version);

create index queuewatch_forecast_sessions_called_idx
  on public.queuewatch_forecast_sessions(actual_called_at)
  where actual_called_at is not null;
```

- [ ] **Step 2: Create prediction table**

```sql
create table public.queuewatch_forecast_predictions (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null unique,
  session_id uuid not null references public.queuewatch_forecast_sessions(id) on delete cascade,
  observed_at timestamptz not null,
  current_position integer not null check (current_position > 0),
  queue_count_same_type integer not null check (queue_count_same_type >= 0),
  historical_speed double precision,
  live_speed double precision,
  effective_speed double precision not null check (effective_speed > 0),
  predicted_minutes double precision not null check (predicted_minutes >= 0),
  predicted_low_minutes double precision not null check (predicted_low_minutes >= 0),
  predicted_high_minutes double precision not null check (predicted_high_minutes >= predicted_low_minutes),
  confidence text not null check (confidence in ('LOW','MEDIUM','HIGH')),
  live_sample_count integer not null default 0 check (live_sample_count >= 0),
  historical_sample_count integer not null default 0 check (historical_sample_count >= 0),
  data_gap boolean not null default false,
  stale_live_data boolean not null default false,
  received_at timestamptz not null default now()
);

create index queuewatch_forecast_predictions_session_observed_idx
  on public.queuewatch_forecast_predictions(session_id, observed_at);

create index queuewatch_forecast_predictions_position_idx
  on public.queuewatch_forecast_predictions(current_position);
```

- [ ] **Step 3: Enable RLS and remove direct client access**

```sql
alter table public.queuewatch_forecast_sessions enable row level security;
alter table public.queuewatch_forecast_predictions enable row level security;

revoke all on public.queuewatch_forecast_sessions from anon, authenticated;
revoke all on public.queuewatch_forecast_predictions from anon, authenticated;
```

Do not add direct INSERT/SELECT policies for mobile clients.

- [ ] **Step 4: Verify schema**

Run read-only checks:
- tables exist;
- primary/foreign keys exist;
- RLS enabled;
- no direct anon/authenticated grants.

- [ ] **Step 5: Run Supabase security and performance advisors**

Fix any telemetry-table issue before continuing. Existing unrelated notices should be reported but not changed without cause.

---

### Task 3: Add an accuracy view for completed sessions

**Files:**
- Supabase SQL only.

**Interfaces:**
- Produces private view `public.queuewatch_forecast_accuracy` for server/admin analysis.

- [ ] **Step 1: Create security-invoker view**

```sql
create view public.queuewatch_forecast_accuracy
with (security_invoker = true)
as
select
  p.id as prediction_id,
  s.forecast_session_id,
  s.algorithm_version,
  s.checkpoint_id,
  s.checkpoint_name,
  s.vehicle_type,
  p.observed_at,
  p.current_position,
  p.queue_count_same_type,
  p.confidence,
  p.predicted_minutes,
  p.predicted_low_minutes,
  p.predicted_high_minutes,
  extract(epoch from (s.actual_called_at - p.observed_at)) / 60.0 as actual_minutes,
  p.predicted_minutes -
    (extract(epoch from (s.actual_called_at - p.observed_at)) / 60.0) as signed_error_minutes,
  abs(
    p.predicted_minutes -
    (extract(epoch from (s.actual_called_at - p.observed_at)) / 60.0)
  ) as absolute_error_minutes,
  (
    extract(epoch from (s.actual_called_at - p.observed_at)) / 60.0
    between p.predicted_low_minutes and p.predicted_high_minutes
  ) as inside_predicted_range
from public.queuewatch_forecast_predictions p
join public.queuewatch_forecast_sessions s on s.id = p.session_id
where s.actual_called_at is not null
  and s.actual_called_at >= p.observed_at;
```

- [ ] **Step 2: Revoke direct client access to the view**

```sql
revoke all on public.queuewatch_forecast_accuracy from anon, authenticated;
```

- [ ] **Step 3: Verify a synthetic completed session**

Insert a temporary server-side test session/prediction, query the view, confirm:
- actual minutes are correct;
- signed error sign matches `predicted - actual`;
- absolute error is positive;
- interval coverage is correct.

Delete only the synthetic test rows afterward.

---

### Task 4: Deploy idempotent ingest Edge Function

**Files:**
- Supabase Edge Function: `submit-queuewatch-forecast/index.ts`
- Supabase Edge Function: `submit-queuewatch-forecast/deno.json`

**Interfaces:**
- Consumes the contract from Task 1.
- Produces HTTP 201/200 for accepted events and bounded errors for invalid/rate-limited requests.

- [ ] **Step 1: Implement request validation**

Rules:
- POST only;
- JSON body only;
- maximum body size 32 KB;
- valid UUID `event_id` and `forecast_session_id`;
- `install_id` length 8..128;
- accepted event types only `prediction`, `actual_call`;
- accepted vehicle type and confidence enums;
- finite numeric fields;
- no field named `regnum`, `registration_number`, `plate`, `mac`, or `device_id`.

Reject forbidden keys with HTTP 400.

- [ ] **Step 2: Implement server-side Supabase client**

Use:

```ts
const supabase = createClient(
  Deno.env.get("SUPABASE_URL") ?? "",
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  { auth: { persistSession: false, autoRefreshToken: false } }
);
```

Never return service-role credentials.

- [ ] **Step 3: Implement idempotent prediction insert**

Pseudo-code must be implemented exactly with server-side conflict handling:
1. upsert session by `forecast_session_id`, preserving first identity fields;
2. insert prediction by unique `event_id`;
3. if `event_id` already exists, return 200 `{"ok":true,"duplicate":true}`.

- [ ] **Step 4: Implement actual_call update**

Find session by `forecast_session_id` + `install_id`.
- if found, set `actual_called_at` and `updated_at`;
- if already set to same or later duplicate event, return success idempotently;
- if session not found, return 409 `unknown_session` so Android retries after pending predictions.

- [ ] **Step 5: Add rate limit**

Limit per `install_id`:
- maximum 240 prediction events/hour;
- maximum 20 actual_call requests/hour.

Use server-side count over `received_at`/session update attempts. Return HTTP 429 with retry-after metadata.

- [ ] **Step 6: Deploy with JWT verification enabled**

Deploy `submit-queuewatch-forecast` with `verify_jwt=true`, matching the existing diagnostic ingestion security pattern.

- [ ] **Step 7: Test function**

Submit:
- valid prediction -> 201;
- duplicate event_id -> 200 duplicate;
- forbidden `regnum` field -> 400;
- actual_call -> 200/201;
- malformed UUID -> 400.

Query tables server-side to verify inserts and no registration number field exists.

- [ ] **Step 8: Run advisors again**

Resolve security/performance findings introduced by the new schema/function.

---

### Task 5: Add Android telemetry client with a bounded offline queue

**Files:**
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastTelemetryModels.kt`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastTelemetryStore.kt`
- Create: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastTelemetryClient.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/ForecastTelemetryStoreTest.kt`
- Modify: `app/build.gradle.kts` for BuildConfig endpoint/key values if not already available.

**Interfaces:**
- Produces:
  - `enqueuePrediction(...)`
  - `enqueueActualCall(...)`
  - `flushPending()`
- Maximum pending events: 500.
- Oldest prediction events are dropped first when cap is exceeded; never drop an unsent `actual_call` while a prediction can be dropped instead.

- [ ] **Step 1: Create random installation ID**

Generate once with `UUID.randomUUID().toString()` and persist under Forecast app SharedPreferences key `forecast_install_id`.

Do not derive it from Android ID, account, device serial, phone number, or regnum.

- [ ] **Step 2: Write queue-cap tests**

Required tests:
- inserting 501 predictions leaves 500;
- actual_call survives cap eviction;
- successful send removes event;
- failed send increments retry metadata without throwing to caller.

- [ ] **Step 3: Implement serialized pending event storage**

Use one JSON array in a dedicated preference file `queuewatch_forecast_telemetry` for V1. Each item includes:
- event JSON;
- created_at;
- attempt_count;
- next_attempt_at.

Cap 500.

- [ ] **Step 4: Implement HTTP sender**

Send to Supabase Edge Function endpoint with:
- `Content-Type: application/json`;
- current Supabase public client authorization mechanism confirmed from Task 1 docs;
- connect/read timeout <= 10 seconds;
- no service-role key.

Accept 200/201 as success.
Treat 400 as permanent/drop-and-log.
Treat 409/429/5xx/network failure as retryable.

- [ ] **Step 5: Add backoff**

Retry delays:
- 1 min;
- 5 min;
- 15 min;
- 60 min;
- capped at 6 hours.

No retry loop may block the tracking coroutine.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/forecast/ForecastTelemetry* \
        app/src/test/java/com/pylikv/queuewatch/forecast/ForecastTelemetryStoreTest.kt \
        app/build.gradle.kts
git commit -m "feat: add resilient Forecast telemetry client"
```

---

### Task 6: Emit prediction and actual-call events from Forecast only

**Files:**
- Modify: `app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt`
- Modify: `app/src/main/java/com/pylikv/queuewatch/forecast/ForecastSessionStore.kt`
- Create: `app/src/test/java/com/pylikv/queuewatch/forecast/ForecastTelemetryPayloadTest.kt`

**Interfaces:**
- Consumes Forecast result and confirmed CALLED.
- Produces privacy-safe events.

- [ ] **Step 1: Add payload privacy test**

Serialize a prediction event and assert:

```kotlin
val json = event.toJson().toString().lowercase()
assertFalse(json.contains("regnum"))
assertFalse(json.contains("registration_number"))
assertFalse(json.contains(carNumber.lowercase()))
```

- [ ] **Step 2: Emit prediction only when materially changed**

Avoid sending every 20-second service poll.

Send a prediction when any is true:
- first available prediction in session;
- position changed;
- ETA changed by >= 10 minutes;
- confidence changed;
- 15 minutes elapsed since last sent prediction.

This limits database volume while preserving accuracy history.

- [ ] **Step 3: Emit actual_call on confirmed CALLED**

Use current `forecast_session_id`; do not include regnum.

Queue event locally before any visible Forecast state is cleared.

- [ ] **Step 4: Flush asynchronously**

After normal snapshot processing, launch telemetry flush on IO scope. Catch all telemetry exceptions inside telemetry component.

- [ ] **Step 5: Regression test**

Simulate a telemetry client that always throws; tracking state calculation must still complete and position/CALLED state must remain correct.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt \
        app/src/main/java/com/pylikv/queuewatch/forecast/ForecastSessionStore.kt \
        app/src/test/java/com/pylikv/queuewatch/forecast/ForecastTelemetryPayloadTest.kt
git commit -m "feat: submit Forecast outcomes without affecting tracking"
```

---

### Task 7: Add accuracy summary queries

**Files:**
- Create: `docs/superpowers/forecast-accuracy-queries.sql`

**Interfaces:**
- Consumes `queuewatch_forecast_accuracy`.
- Produces inspectable queries for model correction.

- [ ] **Step 1: Add overall version quality query**

```sql
select
  algorithm_version,
  count(*) as completed_predictions,
  percentile_cont(0.5) within group (order by absolute_error_minutes) as median_absolute_error_minutes,
  avg(absolute_error_minutes) as mae_minutes,
  percentile_cont(0.5) within group (order by signed_error_minutes) as median_signed_error_minutes,
  avg((inside_predicted_range)::int)::double precision as interval_coverage
from public.queuewatch_forecast_accuracy
group by algorithm_version
order by algorithm_version;
```

- [ ] **Step 2: Add checkpoint/type query**

Group by `algorithm_version, checkpoint_id, checkpoint_name, vehicle_type` with minimum `having count(*) >= 10`.

- [ ] **Step 3: Add position-band query**

Use bands:
- 1–5
- 6–20
- 21–50
- 51–100
- 101+

Calculate median absolute error and signed bias.

- [ ] **Step 4: Add confidence calibration query**

Group by LOW/MEDIUM/HIGH and compare interval coverage.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/forecast-accuracy-queries.sql
git commit -m "docs: add Forecast accuracy analysis queries"
```
