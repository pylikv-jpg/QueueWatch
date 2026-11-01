package com.pylikv.queuewatch.forecast

import org.json.JSONArray
import org.json.JSONObject

data class ShadowForecast(
    val predictedMinutes: Double? = null,
    val unavailableReason: String? = null,
    val positionsPerHour: Double? = null,
    val windowMinutes: Double? = null,
    val movedPositions: Int? = null
)

/** Candidate only: elapsed time includes every observed standstill; no fitted coefficients. */
class ElapsedWindowForecast(private val storage: ForecastStorage) {
    companion object {
        const val VERSION = "elapsed_window_v2"
        private const val KEY = "forecast_elapsed_window_v2"
        private const val WINDOW_MS = 120 * 60_000L
        private const val MIN_WINDOW_MS = 60 * 60_000L
        private const val MAX_GAP_MS = 120_000L
    }
    private data class Point(val at: Long, val position: Int)

    @Synchronized
    fun observe(identity: ForecastTelemetryIdentity, at: Long, position: Int?): ShadowForecast {
        val scope = "${identity.forecastSessionId}|${identity.checkpointId}|${identity.vehicleType.name}"
        val saved = storage.read(KEY)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val points = mutableListOf<Point>()
        if (saved?.optString("scope") == scope) {
            val values = saved.optJSONArray("points") ?: JSONArray()
            for (i in 0 until values.length()) {
                val p = values.getJSONArray(i)
                points.add(Point(p.getLong(0), p.getInt(1)))
            }
        }
        if (position == null || position <= 0) {
            storage.remove(setOf(KEY))
            return ShadowForecast(unavailableReason = "no_confirmed_position")
        }
        val previous = points.lastOrNull()
        if (at < identity.startedAtMillis || previous != null && at <= previous.at) {
            return ShadowForecast(unavailableReason = "non_increasing_time")
        }
        if (previous != null && (at - previous.at > MAX_GAP_MS || position > previous.position)) {
            // Missing observations and backwards renumbering cannot be treated as throughput.
            points.clear()
        }
        points.add(Point(at, position))
        // Keep the real observation just before the cutoff; never interpolate using future data.
        while (points.size > 1 && points[1].at <= at - WINDOW_MS) points.removeAt(0)
        val encoded = JSONArray()
        points.forEach { encoded.put(JSONArray().put(it.at).put(it.position)) }
        storage.write(KEY, JSONObject().put("scope", scope).put("points", encoded).toString())
        val duration = at - points.first().at
        val minutes = duration / 60_000.0
        val moved = points.first().position - position
        fun unavailable(reason: String) = ShadowForecast(unavailableReason = reason,
            windowMinutes = minutes, movedPositions = moved)
        if (position == 1) return unavailable("front_of_queue_waiting_for_call")
        if (duration < MIN_WINDOW_MS) return unavailable("collecting_60_minutes")
        if (moved <= 0) return unavailable("no_movement")
        if (points.zipWithNext().count { (a, b) -> a.position > b.position } < 2) {
            return unavailable("insufficient_movements")
        }
        val speed = moved * 60.0 / minutes
        if (speed > 200.0) return unavailable("implausible_speed")
        return ShadowForecast(position * minutes / moved, null, speed, minutes, moved)
    }
}
