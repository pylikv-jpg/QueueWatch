package com.pylikv.queuewatch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Same persisted observation interval on fresh and restored tracking screens. */
@Composable
internal fun TrackingMovementSummary(evidence: TrackingEvidence?) {
    val message = if (evidence == null) {
        L.text(R.string.movement_unavailable)
    } else {
        // Use confirmed observations, not screen lifetime or time since reopening.
        val totalMinutes = evidence.elapsedMillis / 60_000L
        val elapsed = if (totalMinutes >= 60L) {
            L.text(R.string.duration_hours, totalMinutes / 60L, totalMinutes % 60L)
        } else {
            L.text(R.string.duration_minutes, totalMinutes)
        }
        L.text(R.string.movement, elapsed, evidence.positionsPassed)
    }
    Text(message, modifier = Modifier.fillMaxWidth(), color = Color(0xFFF1F5F9),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
}
