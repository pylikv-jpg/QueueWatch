from pathlib import Path

path = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old_progress = '''    val thresholdProgress =
        if (
            position != null &&
            position!! > 0 &&
            positionAlertThreshold > 0
        ) {

            (
                positionAlertThreshold
                    .toFloat() /
                    position!!
                        .toFloat()
            ).coerceIn(
                0f,
                1f
            )

        } else {

            0f
        }
'''

new_progress = '''    val callProgress =
        when {

            vehicleState == "CALLED" ->
                1f

            position != null &&
                position!! > 0 &&
                queueCount != null &&
                queueCount!! > 0 ->
                (
                    (queueCount!! - position!! + 1)
                        .toFloat() /
                        queueCount!!
                            .toFloat()
                ).coerceIn(
                    0f,
                    1f
                )

            else ->
                0f
        }
'''

if old_progress not in text:
    raise SystemExit("call progress: old progress calculation not found")
text = text.replace(old_progress, new_progress, 1)

replacements = [
    ('''               ШКАЛА ПОРОГА''', '''               ШКАЛА ДО ВЫЗОВА'''),
    ('''                positionAlertEnabled &&\n                vehicleState ==\n                "IN_QUEUE"''', '''                vehicleState == "IN_QUEUE" ||\n                vehicleState == "CALLED"'''),
    ('''                                    "До заданного порога"''', '''                                    "До вызова"'''),
    ('''                                    "≤ $positionAlertThreshold"''', '''                                    if (vehicleState == "CALLED") "ВЫЗВАН" else "ПОЗИЦИЯ 1"'''),
    ('''                                        thresholdProgress''', '''                                        callProgress'''),
    ('''                                    "ДАЛЕКО"''', '''                                    "НАЧАЛО ОЧЕРЕДИ"'''),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"call progress: UI anchor not found: {old!r}")
    text = text.replace(old, new, 1)

old_right = '''                                    if (
                                        position != null &&
                                        position!! <=
                                        positionAlertThreshold
                                    ) {

                                        "ПОРОГ ДОСТИГНУТ"

                                    } else {

                                        "БЛИЗКО"
                                    },

                                color =
                                    if (
                                        position != null &&
                                        position!! <=
                                        positionAlertThreshold
                                    ) {

                                        GreenColor

                                    } else {

                                        SecondaryTextColor
                                    },'''

new_right = '''                                    if (
                                        vehicleState == "CALLED"
                                    ) {

                                        "ВЫЗВАН"

                                    } else if (
                                        position != null &&
                                        position!! <= 5
                                    ) {

                                        "СКОРО ВЫЗОВ"

                                    } else {

                                        "БЛИЖЕ К ВЫЗОВУ"
                                    },

                                color =
                                    if (
                                        vehicleState == "CALLED" ||
                                        (position != null && position!! <= 5)
                                    ) {

                                        GreenColor

                                    } else {

                                        SecondaryTextColor
                                    },'''

if old_right not in text:
    raise SystemExit("call progress: right-side label block not found")
text = text.replace(old_right, new_right, 1)

# Make only the actual progress track and filled segment twice as thick.
outer_bar = '''                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    10.dp
                                )'''
outer_bar_new = '''                            modifier = Modifier
                                .fillMaxWidth()
                                .height(
                                    20.dp
                                )'''
if outer_bar not in text:
    raise SystemExit("call progress: outer progress bar anchor not found")
text = text.replace(outer_bar, outer_bar_new, 1)

inner_bar = '''                                modifier = Modifier
                                    .fillMaxWidth(
                                        callProgress
                                    )
                                    .height(
                                        10.dp
                                    )'''
inner_bar_new = '''                                modifier = Modifier
                                    .fillMaxWidth(
                                        callProgress
                                    )
                                    .height(
                                        20.dp
                                    )'''
if inner_bar not in text:
    raise SystemExit("call progress: inner progress bar anchor not found")
text = text.replace(inner_bar, inner_bar_new, 1)

if "thresholdProgress" in text:
    raise SystemExit("call progress: old thresholdProgress reference remains")

path.write_text(text, encoding="utf-8")
print("Applied call progress fix with 20.dp progress bar")
