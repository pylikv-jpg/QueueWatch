package com.pylikv.queuewatch.forecast

import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** Stores only the current session's emission checkpoint, so restart cannot flood telemetry. */
class ForecastTelemetryRecorder(
    private val storage: ForecastStorage,
    private val client: ForecastTelemetryClient
) {
    companion object { private const val KEY_STATE = "forecast_emission_state" }

    @Synchronized
    fun recordPrediction(
        identity: ForecastTelemetryIdentity, observedAtMillis: Long, currentPosition: Int,
        queueCountSameType: Int, historical: HistoricalEstimate?, live: SpeedEstimate?,
        result: ForecastResult.Available, dataGap: Boolean, staleLiveData: Boolean
    ): Boolean = safely {
        val state = readState(identity)
        if (state.optBoolean("closed") || observedAtMillis < identity.startedAtMillis) return@safely false
        if (state.has("at")) {
            val previousTime = state.getLong("at")
            if (observedAtMillis <= previousTime) return@safely false
            val changed = currentPosition != state.getInt("position") ||
                abs(result.etaMinutes - state.getDouble("eta")) >= 10.0 ||
                result.confidence.name != state.getString("confidence") ||
                observedAtMillis - previousTime >= 15 * 60_000L
            if (!changed) return@safely false
        }
        val id = eventId("prediction", identity.forecastSessionId, observedAtMillis)
        val payload = ForecastTelemetryPayloads.prediction(identity, observedAtMillis,
            currentPosition, queueCountSameType, historical, live, result, dataGap, staleLiveData, id)
        if (!client.enqueue(PendingForecastTelemetryEvent(id, ForecastTelemetryEventType.PREDICTION,
                payload, observedAtMillis, 0, 0L))) return@safely false
        storage.write(KEY_STATE, state.put("at", observedAtMillis).put("position", currentPosition)
            .put("eta", result.etaMinutes).put("confidence", result.confidence.name).toString())
        true
    }

    @Synchronized
    fun recordActualCall(
        identity: ForecastTelemetryIdentity, calledAtMillis: Long, lastInQueueAtMillis: Long
    ): Boolean = safely {
        val state = readState(identity)
        if (state.optBoolean("closed") || lastInQueueAtMillis < identity.startedAtMillis ||
            calledAtMillis < lastInQueueAtMillis || calledAtMillis < state.optLong("at", 0L)) {
            return@safely false
        }
        // A deterministic closure ID survives a process death between enqueue and checkpoint.
        val id = eventId("actual_call", identity.forecastSessionId, 0L)
        val payload = ForecastTelemetryPayloads.actualCall(identity, calledAtMillis, lastInQueueAtMillis, id)
        if (!client.enqueue(PendingForecastTelemetryEvent(id, ForecastTelemetryEventType.ACTUAL_CALL,
                payload, calledAtMillis, 0, 0L))) return@safely false
        storage.write(KEY_STATE, state.put("closed", true).toString())
        true
    }

    private fun readState(identity: ForecastTelemetryIdentity): JSONObject {
        val saved = storage.read(KEY_STATE)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return if (saved?.optString("session") == identity.forecastSessionId) saved
        else JSONObject().put("session", identity.forecastSessionId)
    }

    private fun eventId(type: String, sessionId: String, at: Long) =
        UUID.nameUUIDFromBytes("$type|$sessionId|$at".toByteArray(Charsets.UTF_8)).toString()

    private inline fun safely(action: () -> Boolean): Boolean =
        try { action() } catch (_: Exception) { false }
}
