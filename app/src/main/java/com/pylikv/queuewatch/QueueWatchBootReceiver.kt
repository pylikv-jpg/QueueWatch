package com.pylikv.queuewatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restores an explicitly active QueueWatch session after device reboot or app
 * package replacement. The service reads all parameters from durable storage.
 */
class QueueWatchBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = context.getSharedPreferences(
            QueueWatchService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        if (!prefs.getBoolean(QueueWatchService.KEY_TRACKING_ACTIVE, false)) {
            return
        }

        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, QueueWatchService::class.java)
            )
        } catch (_: Exception) {
            // If an OEM blocks the reboot start, the durable session remains
            // intact and will be restored as soon as the user opens QueueWatch.
        }
    }
}
