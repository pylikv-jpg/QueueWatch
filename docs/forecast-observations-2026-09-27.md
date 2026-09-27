# Forecast observations and candidate comparison

The September 27 analysis found three mobile sessions, one with a confirmed call. On 72 matched observations in that completed session, the displayed v1 MAE was 80.1 minutes and a 120-minute elapsed-time replay was 40.7 minutes. These are correlated observations from one visit, not 72 independent trips. A historical chronological holdout did not consistently improve; no universal improvement is claimed.

This update runs `elapsed_window_v2` in the background. The screen and alerts continue to use v1. Stable application ID and version are unchanged; Forecast is `com.pylikv.queuewatch.forecast`, version code 14 / 1.0.6-forecast.

## Candidate definition

For the tracked vehicle, choose the observed point at or just before `now - 120 minutes`, or the start of the current continuous segment if newer. All elapsed time, including unchanged positions, enters the denominator:

`speed = (position_at_start - position_now) * 60 / elapsed_minutes`

`candidate_wait_minutes = position_now * elapsed_minutes / (position_at_start - position_now)`

The remaining-call estimate uses position rather than position minus one. This is a candidate assumption, not evidence that calls are periodic. No 40-minute period or checkpoint coefficient is invented from the sparse archive. Require at least 60 observed minutes and two positive transitions. No numeric result at position 1, with no progress, or at an implausible rate above 200 positions/hour. Missing polls, observed gaps over two minutes and backwards position changes reset the segment. History is persisted across short restarts, scoped to session, checkpoint and vehicle type. Queue-length changes do not enter the formula.

## New observations

Each tracking check after the vehicle type is known records its outcome (`IN_QUEUE`, `MISSING`, `UNKNOWN`, `SOURCE_ERROR`). Queue positions and counts are null when unavailable. No registration number is transmitted. Before the first reliable type is known, the client does not guess a type or manufacture an observation.

The observation carries nullable baseline and candidate waits, their unavailability reasons and window evidence. It is sent even while both forecasts are unavailable. Network failure does not stop tracking. The existing offline event format is retained, with a 1500-event cap; oldest non-call events are evicted first. Prolonged offline operation can therefore lose older observations. Deterministic IDs prevent duplicate delivery. Confirmed calls retain the last confirmed in-queue timestamp; calls bracketed by more than two minutes are excluded from calibration.

Backend acceptance is additive: existing v1 prediction and actual-call requests remain valid. New observations have their own rate limit (720 per installation/hour). RLS and revoked mobile table grants force writes through the validated endpoint; the accuracy view uses the caller's privileges. No new public table access is granted.

## Verification

- 54 JVM tests: existing regressions plus elapsed-time windows, warmup, idle time, missing observations, restart, type isolation, idempotency and call retention.
- Four Node contract tests check old and new payloads and reject identifiers and invalid evidence.
- Verify full Android builds in CI before distributing APKs.
- Verify deployed serializer → HTTP validator → RPC → accuracy view with synthetic observations, a duplicate, legacy prediction and confirmed call; remove only the synthetic session afterwards.

New observations require installing this Forecast update. Catching up the old archive requires installing QueueLogger 0.2.1 over the existing app and leaving collection active. Its uploader sends up to four batches per table per pass, then continues after one minute while backlog remains. Failures keep acknowledged progress and retry on the normal 30-minute schedule. Actual catch-up time depends on backlog size and connectivity.
