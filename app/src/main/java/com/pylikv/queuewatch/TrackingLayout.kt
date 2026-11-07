package com.pylikv.queuewatch

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Both fresh and restored tracking keep the same stop action outside scrolling content. */
@Composable
internal fun TrackingLayout(onStopped: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        Surface(color = Color(0xFF0B0F14)) {
            Button(
                onClick = {
                    try {
                        // The service owns durable session cleanup, alert cancellation
                        // and notification removal. Do not pretend to stop by editing
                        // the UI's preference flag independently of the service.
                        val accepted = context.startService(Intent(context, QueueWatchService::class.java)
                            .setAction(QueueWatchService.ACTION_STOP_TRACKING))
                        if (accepted != null) onStopped()
                        else Toast.makeText(context, L.text(R.string.stop_tracking_failed), Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, L.text(R.string.stop_tracking_failed), Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303A45),
                    contentColor = Color(0xFFF1F5F9))
            ) {
                Text(L.text(R.string.stop_tracking), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}
