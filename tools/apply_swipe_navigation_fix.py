from pathlib import Path

path = Path("app/src/main/java/com/pylikv/queuewatch/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# Imports.
anchor = "import androidx.activity.compose.setContent\n"
addition = (
    "import androidx.activity.compose.BackHandler\n"
    "import androidx.compose.foundation.gestures.detectHorizontalDragGestures\n"
)
if "import androidx.activity.compose.BackHandler" not in text:
    if anchor not in text:
        raise SystemExit("setContent import anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

anchor = "import androidx.compose.ui.platform.LocalContext\n"
addition = "import androidx.compose.ui.input.pointer.pointerInput\n"
if addition.strip() not in text:
    if anchor not in text:
        raise SystemExit("LocalContext import anchor not found")
    text = text.replace(anchor, addition + anchor, 1)

# Remember whether a monitoring session exists, so a left swipe from setup can return to it.
anchor = '''    var trackingStarted by rememberSaveable {
        mutableStateOf(false)
    }
'''
addition = '''
    var hasTrackingSession by rememberSaveable {
        mutableStateOf(false)
    }
'''
if "var hasTrackingSession by rememberSaveable" not in text:
    if anchor not in text:
        raise SystemExit("trackingStarted anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

# Android back gesture/button on tracking screen returns to setup instead of closing the app.
anchor = '''    var calledAlertEnabled by rememberSaveable {
        mutableStateOf(true)
    }
'''
addition = '''

    BackHandler(
        enabled = trackingStarted
    ) {
        trackingStarted = false
    }
'''
if "BackHandler(\n        enabled = trackingStarted" not in text:
    if anchor not in text:
        raise SystemExit("calledAlertEnabled anchor not found")
    text = text.replace(anchor, anchor + addition, 1)

# Swipe navigation on the whole app surface:
# right on tracking -> setup; left on setup -> current tracking session.
old = '''        Surface(
            modifier =
                Modifier.fillMaxSize(),

            color =
                ScreenBackground
        ) {
'''
new = '''        Surface(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    trackingStarted,
                    hasTrackingSession
                ) {
                    var horizontalDrag = 0f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDrag = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            horizontalDrag += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                horizontalDrag > 100f && trackingStarted ->
                                    trackingStarted = false

                                horizontalDrag < -100f &&
                                    !trackingStarted &&
                                    hasTrackingSession ->
                                    trackingStarted = true
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = {
                            horizontalDrag = 0f
                        }
                    )
                },

            color =
                ScreenBackground
        ) {
'''
if ".pointerInput(\n                    trackingStarted," not in text:
    if old not in text:
        raise SystemExit("app Surface anchor not found")
    text = text.replace(old, new, 1)

# Starting/restarting tracking records that a current session exists.
old = '''                    onStartTracking = {
                        trackingStarted = true
                    }
'''
new = '''                    onStartTracking = {
                        hasTrackingSession = true
                        trackingStarted = true
                    }
'''
if "hasTrackingSession = true\n                        trackingStarted = true" not in text:
    if old not in text:
        raise SystemExit("onStartTracking anchor not found")
    text = text.replace(old, new, 1)

required = [
    "BackHandler(",
    ".pointerInput(",
    "detectHorizontalDragGestures(",
    "hasTrackingSession = true",
]
for marker in required:
    if marker not in text:
        raise SystemExit(f"navigation patch validation failed: {marker}")

path.write_text(text, encoding="utf-8")
print("QueueWatch swipe/back navigation patch applied")
