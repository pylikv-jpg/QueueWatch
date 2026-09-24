# QueueWatch Forecast pilot — 24 September 2026

The finished pilot is isolated on `feature/forecast-pilot-20260924`, including upstream commits through `feb0526`. Concurrent updates on `feature/forecast-experiment` were reviewed and merged forward; its original movement contract tests are preserved, with additional stationary/gap tests. Stable main remains `ed0f53c`. The pilot is `com.pylikv.queuewatch.forecast`, version 1.0.5-forecast (13); stable retains its existing ID and version. A distinct teal clock icon identifies Forecast.

Implemented: batch-aware live movement, stationary-time accounting, suppression during observed stalls, independent historical fallback, range/confidence, session isolation, setup and tracking telemetry switches, bounded retry queue, and private Supabase ingestion/accuracy queries. Unknown/disappeared vehicles and forecasts never establish CALLED; only explicit server status 3 closes a telemetry session.

## Changes to the draft calculation

The draft's median of positive per-poll speeds excluded stationary time and overestimated bursty queues. V1.1 uses total batch advancement over covered observation time; one transition contributes one advance. It requires ten observed minutes for live speed, resets across gaps over ten minutes, rejects unsupported isolated jumps/reversals, and suppresses ETA after a sustained observed stall. Historical baselines use non-overlapping, adequately observed hourly blocks including zero movement, rather than treating every moved car as an independent sample.

Position 1 displays “Вы в начале очереди”; it is not a confirmed call. Invalid positions, future timestamps, non-finite speeds and empty speed samples are rejected. Archive data older than seven days forces LOW confidence; older than thirty days is unavailable. No prediction-based alarms have been enabled.

## Archive evidence

Source: `pylikv-jpg/QueueLoggerData`, commit `1776b5f9948dd2dc9669429b6de999d3449fd904`. All 273 exported event files were read, plus the 78 queue sample files covering the event period. There were 272,000 distinct event records, 1,000 duplicate rows, and no conflicting event IDs. Event timestamps cover September 1–4 even though upload filenames continue through September 24: the movement/event export has a substantial backlog. Queue-size snapshots alone cannot substitute for recent throughput.

Replay reconstructed 2,311 completed sessions with reliable observation brackets, excluded 1,388 uncertain call endpoints, and evaluated 4,706 available predictions out of 7,795 candidate position-band observations. Each session contributes at most one observation per band. Historical inputs use only prior blocks; error calibration uses only calls already observed by each prediction time. The last third of chronological time contains 2,034 available predictions: median absolute error 27.3 minutes, MAE 65.8 minutes, p80 error 91.8 minutes, interval coverage 77.0%.

These are historical replay results, not phone field accuracy. The event replay's positive-movement medians approximate snapshots; Android has an additional guard against unsupported isolated movements. Results vary sharply by checkpoint/type and include large errors. Seven sufficiently populated global baseline cells are shipped; time-of-day cells are withheld because the sample is too sparse. See `analysis/forecast_v1/output/backtest_v1.json` for per-group details. Raw registration identifiers are never exported in the baseline/report.

## Backend

Existing Supabase project reused, existing TachoWatch diagnostics untouched. Tables `queuewatch_forecast_sessions` and `queuewatch_forecast_predictions` have RLS and no direct client privileges; accuracy view uses security-invoker. `submit-queuewatch-forecast` verifies JWTs, accepts only the explicit event schema, limits body size, validates timestamps and finite fields, and calls a service-role-only security-invoker transaction. A public anon credential in the APK does not identify a trusted individual; random install IDs plus rate limiting are suitable for the pilot, not strong protection against deliberate fabricated telemetry.

HTTP smoke verification passed: prediction accepted, duplicate idempotent, unknown/plate field rejected, actual-call idempotent, direct anonymous table read denied. Accuracy view produced exactly one smoke outcome and correct error. Synthetic rows were deleted after verification. Security advisors report only informational “RLS enabled without policies”, deliberate deny-all behavior. No automatic model self-training is performed.

Retry queue is capped at 500, prioritizing actual-call events over predictions. Calls include their session metadata so lost/evicted predictions cannot leave an orphan closure retrying forever. Sends are separate from the monitoring coroutine. Retries occur during active monitoring and resume on the next monitoring launch; stopping the service does not schedule an independent uploader.

## Remaining field checks / promotion gate

Install beside stable on Android 16, select the same vehicle/checkpoint, compare position/alerts, background the app and reopen it, test network loss, and confirm both threshold/call acknowledgements. Emulator/device execution is not implied by compile/unit tests. Confirm one real Forecast session reaches the server. Keep main unchanged until enough completed field sessions exist and per-checkpoint error/coverage are acceptable. Model corrections require a new version and chronological regression check.

The logger export backlog needs separate investigation; its production code was not changed as part of this pilot.
