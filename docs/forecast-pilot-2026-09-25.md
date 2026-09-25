# QueueWatch Forecast 1.0.5 — pilot

Separate application `com.pylikv.queuewatch.forecast`, version code 13. Stable main is not merged by this change.

The app estimates waiting time from observed queue movements. Historical cells remain disabled. It records predictions and confirmed call observations with random installation/session identifiers, without plate numbers, for later error analysis. It queues offline deliveries, retains event IDs across retries and keeps network failure outside queue tracking. An IN_QUEUE vehicle at position 1 has unknown call time, not a guaranteed immediate call.

## Evidence
- 45 Forecast JVM tests pass, including offline handling, restart suppression, idempotent closure, movement timing, and position-1 uncertainty.
- 13 archive pipeline tests pass; private archive CI 36114347279 passed on d0b97f62696480645a74c08d38bcd44ac1bb3438.
- Production serializer/transport smoke accepted a prediction, identical retry and actual call; only one prediction remained. Invalid payload was permanent failure. Synthetic rows were removed.
- Accuracy SQL tested with one timely and one two-hour-offline call: all calibration aggregates now exclude the uncertain case.
- Fresh reviewer raised three Important findings (front certainty, exported fallback shadowing, offline call filtering); each reproduced before correction, then passed. No Critical or deferred Minor finding.

## Decisions and limits
1. Retain deployed public anonymous JWT transport authorization with JWT validation; no service key in APK. Using a different authorization format would reject telemetry.
2. Retain existing bounded Base64 event queue encoding rather than migrate it to the draft JSON-array form; avoids dropping queued events.
3. Follow the actual deployed contract rather than outdated plan fields: started_at required, no checkpoint_name, accuracy uses in_interval. Wrong fields would be rejected.
4. Measure queue speed between movements, including idle polls; reject unobserved gaps longer than 15 minutes. Cost: no speed through such gaps.
5. Withhold historical cells despite adequate raw counts. Detailed events end on September 4 despite later upload filenames, and no TRUCK model is validated. Final held-out hybrid: 198 cases, median absolute error 65.7 minutes, MAE 72.4 minutes, interval coverage 63.1%. Strategies have different usable case counts, so these are not a paired comparison. See forecast-evaluation-2026-09-25.json.
6. Override the draft position-1 zero-ETA special case: call timing remains unknown at the front. Cost: no numeric estimate for that position.
7. Preserve -1 as the global historical fallback marker; exact hourly buckets must take priority. Candidate output is not shipped.
8. Use only call observations with <=2-minute last-seen-to-call intervals for calibration. Cost: fewer eligible cases when the phone is offline.
9. Reviewer did not independently repeat server checks or Android/device lifecycle tests. Server checks above were performed by the implementer. Real-phone behavior and forecast quality need pilot observations; no accuracy guarantee is made.
