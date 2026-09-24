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
    '"movement_session_id"',
    "val positionsPassed =",
]

missing = [marker for marker in required_service if marker not in service]
missing += [marker for marker in required_main if marker not in main]

if missing:
    raise SystemExit(
        "Missing materialized shipped fixes: " + ", ".join(missing)
    )

print("Materialized shipped fixes verified")
