from pathlib import Path

path = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Session movement state is stored in the same SharedPreferences as the service,
# so opening the setup screen and swiping back does not lose the baseline.
anchor = '''    var lastUpdate by remember {
        mutableStateOf("")
    }
'''
addition = '''

    var trackingStartPosition by remember {
        mutableStateOf<Int?>(null)
    }

    var trackingStartTime by remember {
        mutableStateOf<Long?>(null)
    }

    var movementElapsedMs by remember {
        mutableStateOf(0L)
    }
'''
if "var trackingStartPosition by remember" not in text:
    if anchor not in text:
        raise SystemExit("movement info: lastUpdate state anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

# Load/create a baseline for this exact car + checkpoint. A different tracked
# vehicle starts a fresh movement measurement automatically.
anchor = '''            lastUpdate =
                preferences.getString(
                    QueueWatchService.KEY_LAST_UPDATE,
                    ""
                ) ?: ""
'''
addition = '''

            val movementSessionId =
                carNumber.trim().uppercase() + "|" + checkpointName.trim().uppercase()

            val savedMovementSessionId =
                preferences.getString(
                    "movement_session_id",
                    ""
                ) ?: ""

            if (
                vehicleState == "IN_QUEUE" &&
                position != null &&
                position!! > 0
            ) {
                if (savedMovementSessionId != movementSessionId) {
                    val now = System.currentTimeMillis()
                    preferences.edit()
                        .putString("movement_session_id", movementSessionId)
                        .putInt("movement_start_position", position!!)
                        .putLong("movement_start_time", now)
                        .apply()

                    trackingStartPosition = position
                    trackingStartTime = now
                    movementElapsedMs = 0L
                } else {
                    trackingStartPosition =
                        if (preferences.contains("movement_start_position")) {
                            preferences.getInt("movement_start_position", 0)
                        } else {
                            null
                        }

                    trackingStartTime =
                        if (preferences.contains("movement_start_time")) {
                            preferences.getLong("movement_start_time", 0L)
                        } else {
                            null
                        }

                    movementElapsedMs =
                        trackingStartTime?.let { start ->
                            (System.currentTimeMillis() - start).coerceAtLeast(0L)
                        } ?: 0L
                }
            } else if (savedMovementSessionId == movementSessionId) {
                trackingStartPosition =
                    if (preferences.contains("movement_start_position")) {
                        preferences.getInt("movement_start_position", 0)
                    } else {
                        null
                    }

                trackingStartTime =
                    if (preferences.contains("movement_start_time")) {
                        preferences.getLong("movement_start_time", 0L)
                    } else {
                        null
                    }

                movementElapsedMs =
                    trackingStartTime?.let { start ->
                        (System.currentTimeMillis() - start).coerceAtLeast(0L)
                    } ?: 0L
            }
'''
if '"movement_session_id"' not in text:
    if anchor not in text:
        raise SystemExit("movement info: lastUpdate read anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

# Derived, factual movement values only; no forecast.
anchor = '''    val statusText =
        when (
            vehicleState
        ) {
'''
addition = '''    val positionsPassed =
        if (
            trackingStartPosition != null &&
            position != null
        ) {
            (trackingStartPosition!! - position!!).coerceAtLeast(0)
        } else {
            null
        }

    val positionsToCall =
        when {
            vehicleState == "CALLED" -> 0
            position != null && position!! > 0 -> position
            else -> null
        }

    val elapsedTotalMinutes = movementElapsedMs / 60_000L
    val elapsedHours = elapsedTotalMinutes / 60L
    val elapsedMinutes = elapsedTotalMinutes % 60L
    val elapsedText =
        if (elapsedHours > 0L) {
            "${elapsedHours} ч ${elapsedMinutes} мин"
        } else {
            "${elapsedMinutes} мин"
        }

'''
if "val positionsPassed =" not in text:
    if anchor not in text:
        raise SystemExit("movement info: statusText anchor not found")
    text = text.replace(anchor, addition + anchor, 1)

# Add two compact factual lines at the bottom of the progress-to-call card.
anchor = '''                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                text =
                                    "НАЧАЛО ОЧЕРЕДИ",'''
if anchor not in text:
    raise SystemExit("movement info: call-progress lower row anchor not found")

# Insert after the lower label Row, using the known post-call-progress block ending.
old = '''                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
'''
new = '''                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                if (positionsPassed != null) {
                                    "За $elapsedText пройдено: $positionsPassed позиций"
                                } else {
                                    "За — пройдено: — позиций"
                                },
                            color = MainTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text =
                                "До вызова осталось: ${positionsToCall?.toString() ?: "—"} позиций",
                            color =
                                if (vehicleState == "CALLED") GreenColor else SecondaryTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
'''
# Need the occurrence inside the progress card. Restrict replacement to text after its marker.
marker = '"НАЧАЛО ОЧЕРЕДИ"'
marker_index = text.find(marker)
if marker_index < 0:
    raise SystemExit("movement info: post-call-progress marker not found")
end_index = text.find(old, marker_index)
if end_index < 0:
    raise SystemExit("movement info: progress card ending not found")
text = text[:end_index] + text[end_index:].replace(old, new, 1)

required = [
    '"movement_session_id"',
    "val positionsPassed =",
    '"За $elapsedText пройдено: $positionsPassed позиций"',
    '"До вызова осталось: ${positionsToCall?.toString() ?: "—"} позиций"',
]
for item in required:
    if item not in text:
        raise SystemExit(f"movement info validation failed: {item}")

path.write_text(text, encoding="utf-8")
print("Applied queue movement information")
