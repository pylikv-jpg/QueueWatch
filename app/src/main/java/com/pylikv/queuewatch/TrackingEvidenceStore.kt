package com.pylikv.queuewatch

import com.pylikv.queuewatch.forecast.ForecastStorage
import org.json.JSONObject

data class TrackingEvidence(
    val firstPosition: Int,
    val firstAt: Long,
    val lastPosition: Int,
    val lastAt: Long
) {
    val positionsPassed: Int get() = (firstPosition - lastPosition).coerceAtLeast(0)
    val elapsedMillis: Long get() = (lastAt - firstAt).coerceAtLeast(0)
    fun isFresh(now: Long): Boolean = now - lastAt in 0L..120_000L
}

/** Written by the monitoring service, including while the screen is closed. */
class TrackingEvidenceStore(private val storage: ForecastStorage) {
    companion object { private const val KEY = "tracking_evidence_v1" }

    fun read(scope: String): TrackingEvidence? = runCatching {
        val row = JSONObject(storage.read(KEY) ?: return null)
        if (row.getString("scope") != scope) return null
        TrackingEvidence(row.getInt("first_position"), row.getLong("first_at"),
            row.getInt("last_position"), row.getLong("last_at"))
    }.getOrNull()

    fun observe(scope: String, position: Int, at: Long): TrackingEvidence {
        require(position > 0 && at > 0)
        val prior = read(scope)
        if (prior != null && at < prior.lastAt) return prior
        val value = TrackingEvidence(prior?.firstPosition ?: position, prior?.firstAt ?: at, position, at)
        storage.write(KEY, JSONObject().put("scope", scope)
            .put("first_position", value.firstPosition).put("first_at", value.firstAt)
            .put("last_position", value.lastPosition).put("last_at", value.lastAt).toString())
        return value
    }

    fun reset() = storage.remove(setOf(KEY))
}

/** A saved live state is only current when the vehicle itself was seen recently. */
fun displayedTrackingState(savedState: String, evidence: TrackingEvidence?, now: Long): String =
    if (savedState == "IN_QUEUE" && evidence?.isFresh(now) != true) "STALE" else savedState
