package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType

/** Independent of queue alerts and UI. Input vehicle identifiers are used locally only. */
class ForecastAnalyticsSession(
    private val storage: ForecastStorage,
    private val client: ForecastTelemetryClient
) {
    private val sessions = ForecastSessionStore(storage)
    private val recorder = ForecastTelemetryRecorder(storage, client, minimumIntervalMillis = 60_000L)
    private val shadow = ElapsedWindowForecast(storage)
    private val live = mutableMapOf<VehicleType, LiveMovementEstimator>()
    private var identity: ForecastTelemetryIdentity? = null
    private var lastPositionAt = 0L

    fun observe(
        localSessionKey: String, checkpointId: String, vehicleType: VehicleType, at: Long,
        currentPosition: Int, queueCount: Int, queuePositions: Map<String, Int>,
        historical: HistoricalEstimate?
    ) {
        if (currentPosition <= 0) return
        if (sessions.calledAtMillis(localSessionKey) != null ||
            sessions.lastVehicleType(localSessionKey)?.let { it != vehicleType } == true) {
            sessions.reset()
            live.clear()
        }
        val id = sessions.ensureSession(localSessionKey, at)
        if (identity?.forecastSessionId != id) {
            live.clear()
            lastPositionAt = sessions.lastInQueueAtMillis(localSessionKey) ?: 0L
        }
        sessions.saveVehicleType(localSessionKey, vehicleType)
        val started = sessions.startedAtMillis(localSessionKey) ?: return
        val value = ForecastTelemetryIdentity(ForecastInstallationId(storage).getOrCreate(),
            id, "v1", checkpointId, vehicleType, started)
        val dataGap = lastPositionAt > 0 && at - lastPositionAt > 120_000L
        lastPositionAt = at
        identity = value
        sessions.markInQueue(localSessionKey, at)
        val movement = live.getOrPut(vehicleType) { LiveMovementEstimator() }
        movement.observePositions(at, queuePositions)
        val speed = movement.estimate(at)
        val baseline = ForecastEngineV1.estimate(ForecastInput(currentPosition, queueCount, historical, speed, at))
        val candidate = shadow.observe(value, at, currentPosition)
        recorder.recordObservation(value, at, "IN_QUEUE", currentPosition, queueCount, baseline, candidate, dataGap)
        if (baseline is ForecastResult.Available) recorder.recordPrediction(value, at, currentPosition,
            queueCount, historical, speed, baseline, dataGap,
            speed == null || at - speed.newestSampleAtMillis !in 0L..3_600_000L)
    }

    private fun storedIdentity(localSessionKey: String, checkpointId: String): ForecastTelemetryIdentity? {
        val id = sessions.currentSessionId(localSessionKey) ?: return null
        val started = sessions.startedAtMillis(localSessionKey) ?: return null
        val type = sessions.lastVehicleType(localSessionKey) ?: return null
        val install = storage.read("forecast_install_id") ?: return null
        return ForecastTelemetryIdentity(install, id, "v1", checkpointId, type, started)
    }

    fun unavailable(localSessionKey: String, checkpointId: String, at: Long, state: String) {
        val value = storedIdentity(localSessionKey, checkpointId) ?: return
        val candidate = shadow.observe(value, at, null)
        recorder.recordObservation(value, at, state, null, null,
            ForecastResult.Unavailable(state.lowercase()), candidate, true)
    }

    fun called(localSessionKey: String, checkpointId: String, at: Long) {
        val value = storedIdentity(localSessionKey, checkpointId) ?: return
        val last = sessions.lastInQueueAtMillis(localSessionKey) ?: return
        if (recorder.recordActualCall(value, at, last)) sessions.markCalled(localSessionKey, at)
    }

    fun flush(at: Long, allowed: () -> Boolean) = client.flushPending(at, allowed)
}
