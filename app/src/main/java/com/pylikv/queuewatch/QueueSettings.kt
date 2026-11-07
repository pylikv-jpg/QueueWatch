package com.pylikv.queuewatch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun QueueSettingsButton() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(L.text(R.string.settings)) }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(L.text(R.string.settings)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                LanguageSelector()
                Spacer(Modifier.height(12.dp))
                ForecastAnalyticsControl()
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(L.text(R.string.close)) } }
    )
}

@Composable
internal fun ParticipationWelcome() {
    val context = LocalContext.current
    val consent = remember { analyticsConsent(context) }
    var open by remember { mutableStateOf(consent.welcomePending()) }
    if (open) AlertDialog(
        onDismissRequest = { consent.disable(); open = false },
        title = { Text(L.text(R.string.welcome_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(L.text(R.string.welcome_body))
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/pylikv-jpg/QueueWatch/blob/main/privacy-policy.md")))
                }) { Text(L.text(R.string.privacy_policy)) }
            }
        },
        confirmButton = { TextButton(onClick = { consent.enable(); open = false }) {
            Text(L.text(R.string.welcome_yes))
        } },
        dismissButton = { TextButton(onClick = { consent.disable(); open = false }) {
            Text(L.text(R.string.welcome_no))
        } }
    )
}

@Composable
internal fun TrackingHealthStatus() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(QueueWatchService.PREFS_NAME, Context.MODE_PRIVATE) }
    var health by remember { mutableStateOf(MonitoringHealth.UNCONFIRMED) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(prefs) {
        while (true) {
            health = monitoringHealth(
                prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false),
                prefs.getLong(QueueWatchService.KEY_HEARTBEAT, 0), System.currentTimeMillis(),
                prefs.getString(QueueWatchService.KEY_STATE, "").orEmpty())
            notificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            delay(1_000)
        }
    }
    val label = when (health) {
        MonitoringHealth.STOPPED -> R.string.health_stopped
        MonitoringHealth.UNCONFIRMED -> R.string.health_unconfirmed
        MonitoringHealth.RETRYING -> R.string.health_retrying
        MonitoringHealth.SEARCHING -> R.string.health_searching
        MonitoringHealth.RUNNING -> R.string.health_running
    }
    Column {
        Text(L.text(label), color = when (health) {
            MonitoringHealth.RUNNING -> Color(0xFF3DDC84)
            MonitoringHealth.SEARCHING -> Color(0xFF4DA3FF)
            else -> Color(0xFFFFC857)
        })
        if (!notificationsEnabled) TextButton(onClick = {
            context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
        }) { Text(L.text(R.string.notifications_disabled), color = Color(0xFFFFC857)) }
    }
}
