package com.pylikv.queuewatch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Launcher that restores an active monitoring session after process death.
 *
 * If there is no durable session, the normal setup MainActivity is opened.
 * If monitoring is active, the user goes straight to the tracking screen and
 * the service is explicitly nudged to restore from SharedPreferences.
 */
class SessionLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(
            QueueWatchService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)) {
            openSetupAndFinish()
            return
        }

        // User opening the app is a valid foreground-service start trigger.
        // No extras are needed: QueueWatchService restores the durable session.
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, QueueWatchService::class.java)
            )
        } catch (_: Exception) {
        }

        setContent {
            RestoredTrackingScreen()
        }
    }

    private fun openSetupAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

/** Uses the same UI as a newly started session, including Forecast and movement. */
@Composable
private fun RestoredTrackingScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences(QueueWatchService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var active by remember {
        mutableStateOf(prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false))
    }
    var carNumber by remember {
        mutableStateOf(prefs.getString(QueueWatchService.KEY_CAR_NUMBER, "").orEmpty())
    }
    var checkpoint by remember {
        mutableStateOf(prefs.getString(QueueWatchService.KEY_CHECKPOINT, "").orEmpty())
    }

    LaunchedEffect(Unit) {
        while (true) {
            active = prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)
            carNumber = prefs.getString(QueueWatchService.KEY_CAR_NUMBER, "").orEmpty()
            checkpoint = prefs.getString(QueueWatchService.KEY_CHECKPOINT, "").orEmpty()
            delay(1_000)
        }
    }

    LaunchedEffect(active) {
        if (!active) {
            context.startActivity(Intent(context, MainActivity::class.java))
            activity?.finish()
        }
    }

    if (active) {
        MaterialTheme {
            TrackingScreen(
                carNumber = carNumber,
                checkpointName = checkpoint,
                positionAlertEnabled = prefs.getBoolean(
                    QueueWatchService.KEY_POSITION_ALERT_ENABLED, true
                ),
                positionAlertThreshold = prefs.getInt(
                    QueueWatchService.KEY_POSITION_THRESHOLD, 100
                ),
                calledAlertEnabled = prefs.getBoolean(
                    QueueWatchService.KEY_CALLED_ALERT_ENABLED, true
                ),
                startMonitoring = false,
                onTrackingStopped = { active = false }
            )
        }
    }
}
