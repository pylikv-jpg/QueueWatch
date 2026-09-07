package com.pylikv.queuewatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class QueueWatchBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val preferences = context.getSharedPreferences(
            QueueWatchService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!preferences.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)) {
            return
        }

        val serviceIntent = Intent(
            context,
            QueueWatchService::class.java
        ).apply {
            this.action = QueueWatchService.ACTION_RESTORE_MONITORING
        }

        try {
            ContextCompat.startForegroundService(
                context,
                serviceIntent
            )
        } catch (_: Exception) {
            // State remains persisted. Opening QueueWatch will restore tracking.
        }
    }
}
