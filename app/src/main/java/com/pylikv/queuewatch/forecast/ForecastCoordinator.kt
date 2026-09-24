package com.pylikv.queuewatch.forecast

import android.content.Context
import com.pylikv.queuewatch.QueueVehicle
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Local state is private to the Forecast package; plates never enter event payloads. */
class ForecastCoordinator(context: Context) {
    private val prefs = context.getSharedPreferences("queuewatch_forecast", Context.MODE_PRIVATE)
    private val baselines = HistoricalBaselineRepository(context)
    private val telemetry = ForecastTelemetryClient(context)
    private var estimator = LiveMovementEstimator()
    private var activeKey: String? = null

    fun observe(car: String, checkpoint: String, checkpointName: String, vehicles: List<QueueVehicle>, vehicle: QueueVehicle?, now: Long) {
        val key = car.uppercase().replace(Regex("[\\s-]"), "") + "|" + checkpoint
        // Identity includes the server registration time when available so a later
        // visit by the same vehicle creates another experiment session.
        val registration = vehicle?.registrationDate.orEmpty()
        val changedVisit = registration.isNotBlank() && prefs.getString("registration", "").orEmpty().let { it.isNotBlank() && it != registration }
        if (prefs.getString("car_key", null) != key || changedVisit ||
            (prefs.getBoolean("completed", false) && vehicle?.status == 2 && (vehicle.position ?: 0) > 0)) {
            prefs.edit().clear().putString("car_key", key).putString("session_id", UUID.randomUUID().toString())
                .putLong("started", now).putString("registration", registration).commit()
            estimator = LiveMovementEstimator()
        }
        if (activeKey != key) { estimator = LiveMovementEstimator(); activeKey = key }
        prefs.edit().putString("checkpoint_name", checkpointName).apply()
        if (registration.isNotBlank()) prefs.edit().putString("registration", registration).apply()
        if (vehicle == null) { unavailable("Данные автомобиля временно отсутствуют", now); telemetry.flush(); return }
        if (vehicle.status == 3) {
            val lastQueue = prefs.getLong("last_in_queue", 0)
            if (!prefs.getBoolean("completed", false) && lastQueue > 0) {
                telemetry.enqueue(baseEvent("actual_call", checkpoint, vehicle.vehicleType.name)
                    .put("called_at", iso(now)).put("last_in_queue_at", iso(lastQueue)))
                prefs.edit().putBoolean("completed", true).commit()
            }
            unavailable("Вызов подтверждён сервером", now); telemetry.flush(); return
        }
        val position = vehicle.position
        if (vehicle.status != 2 || position == null || position <= 0) {
            unavailable("Ожидаем подтверждённую позицию в очереди", now); telemetry.flush(); return
        }
        val liveVehicles = vehicles.filter { it.vehicleType == vehicle.vehicleType && it.status == 2 && (it.position ?: 0) > 0 }
        estimator.observePositions(now, liveVehicles.associate { it.regnum.uppercase() to it.position!! })
        prefs.edit().putLong("last_in_queue", now).apply()
        val live = estimator.estimate(now)
        val history = baselines.find(checkpoint, vehicle.vehicleType.name, now)
        val result = if (estimator.stationary(now)) ForecastResult.Unavailable("queue_stationary") else
            ForecastEngineV1.estimate(ForecastInput(position, liveVehicles.size, history, live, now))
        if (result is ForecastResult.Available) {
            val previousEta = prefs.getFloat("sent_eta", -1f).toDouble()
            val since = now - prefs.getLong("sent_at", 0)
            val changed = position != prefs.getInt("sent_position", -1) || kotlin.math.abs(previousEta - result.etaMinutes) >= 10 ||
                prefs.getString("sent_confidence", "") != result.confidence.name
            prefs.edit().putBoolean("available", true).putFloat("eta", result.etaMinutes.toFloat())
                .putFloat("low", result.lowMinutes.toFloat()).putFloat("high", result.highMinutes.toFloat())
                .putString("confidence", result.confidence.name).putFloat("speed", result.effectivePositionsPerHour.toFloat())
                .putBoolean("calibrated", (history?.absoluteErrorP80Minutes ?: 0.0) > 0)
                .putLong("history_cutoff", history?.dataCutoffMillis ?: 0)
                .putLong("updated", now).putInt("position", position).apply()
            if (telemetry.enabled && (since >= 900_000 || (since >= 60_000 && changed))) {
                telemetry.enqueue(baseEvent("prediction", checkpoint, vehicle.vehicleType.name)
                    .put("observed_at", iso(now)).put("current_position", position).put("queue_count_same_type", liveVehicles.size)
                    .put("historical_speed", history?.positionsPerHour ?: JSONObject.NULL).put("live_speed", live?.positionsPerHour ?: JSONObject.NULL)
                    .put("effective_speed", result.effectivePositionsPerHour).put("predicted_minutes", result.etaMinutes)
                    .put("predicted_low_minutes", result.lowMinutes).put("predicted_high_minutes", result.highMinutes)
                    .put("confidence", result.confidence.name).put("live_sample_count", live?.sampleCount ?: 0)
                    .put("historical_sample_count", history?.sampleCount ?: 0).put("data_gap", estimator.dataGap)
                    .put("stale_live_data", live == null))
                prefs.edit().putLong("sent_at", now).putInt("sent_position", position)
                    .putFloat("sent_eta", result.etaMinutes.toFloat()).putString("sent_confidence", result.confidence.name).apply()
            }
        } else unavailable(if (estimator.stationary(now)) "Продвижения пока нет — время вызова неизвестно" else
            "Недостаточно данных для прогноза. Наблюдаем движение очереди…", now)
        telemetry.flush()
    }

    private fun baseEvent(type: String, checkpoint: String, vehicleType: String) = JSONObject()
        .put("event_id", UUID.randomUUID().toString()).put("event_type", type).put("install_id", telemetry.installId)
        .put("forecast_session_id", prefs.getString("session_id", "")).put("algorithm_version", ForecastEngineV1.VERSION)
        .put("checkpoint_id", checkpoint).put("vehicle_type", vehicleType).put("started_at", iso(prefs.getLong("started", 0)))
    private fun iso(time: Long) = Instant.ofEpochMilli(time).toString()
    fun unavailable(message: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putBoolean("available", false).putString("message", message).putLong("updated", now).apply()
    }
    fun stop() { prefs.edit().clear().commit(); estimator = LiveMovementEstimator(); activeKey = null; telemetry.flush() }
    fun close() { telemetry.close() }
}
