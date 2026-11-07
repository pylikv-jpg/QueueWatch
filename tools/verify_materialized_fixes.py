from pathlib import Path

service = Path("app/src/main/java/com/pylikv/queuewatch/QueueWatchService.kt").read_text(encoding="utf-8")
main = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt").read_text(encoding="utf-8")

required_service = [
    "sameTypeLiveQueueCount",
    "while (preferences.getBoolean(KEY_TRACKING_ACTIVE, false))",
]

required_main = [
    "BackHandler(",
    "detectHorizontalDragGestures(",
    "val callProgress =",
    "TrackingEvidenceStore(",
    "TrackingMovementSummary(",
]

missing = [marker for marker in required_service if marker not in service]
missing += [marker for marker in required_main if marker not in main]
restored = Path("app/src/main/java/com/pylikv/queuewatch/SessionLauncherActivity.kt").read_text(encoding="utf-8")
if "TrackingMovementSummary(" not in restored:
    missing.append("TrackingMovementSummary on restored screen")

if missing:
    raise SystemExit(
        "Missing materialized shipped fixes: " + ", ".join(missing)
    )

print("Materialized shipped fixes verified")
