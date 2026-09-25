package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ForecastTelemetryIdentity(
    val installId: String,
    val forecastSessionId: String,
    val algorithmVersion: String,
    val checkpointId: String,
    val vehicleType: VehicleType,
    val startedAtMillis: Long
)

class ForecastInstallationId(
    private val storage: ForecastStorage,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    @Synchronized
    fun getOrCreate(): String {
        val saved = storage.read("forecast_install_id")
        if (!saved.isNullOrBlank()) return saved
        return idFactory().also { storage.write("forecast_install_id", it) }
    }
}

/** Exact server allow-list. There is deliberately no vehicle-number argument. */
object ForecastTelemetryPayloads {
    private fun base(identity: ForecastTelemetryIdentity, eventId: String, type: String) =
        JSONObject()
            .put("event_id", eventId)
            .put("event_type", type)
            .put("install_id", identity.installId)
            .put("forecast_session_id", identity.forecastSessionId)
            .put("algorithm_version", identity.algorithmVersion)
            .put("checkpoint_id", identity.checkpointId)
            .put("vehicle_type", identity.vehicleType.name)
            .put("started_at", Instant.ofEpochMilli(identity.startedAtMillis).toString())

    fun prediction(
        identity: ForecastTelemetryIdentity,
        observedAtMillis: Long,
        currentPosition: Int,
        queueCountSameType: Int,
        historical: HistoricalEstimate?,
        live: SpeedEstimate?,
        result: ForecastResult.Available,
        dataGap: Boolean,
        staleLiveData: Boolean,
        eventId: String = UUID.randomUUID().toString()
    ): String = base(identity, eventId, "prediction")
        .put("observed_at", Instant.ofEpochMilli(observedAtMillis).toString())
        .put("current_position", currentPosition)
        .put("queue_count_same_type", queueCountSameType)
        .put("historical_speed", historical?.positionsPerHour ?: JSONObject.NULL)
        .put("live_speed", live?.positionsPerHour ?: JSONObject.NULL)
        .put("effective_speed", result.effectivePositionsPerHour)
        .put("predicted_minutes", result.etaMinutes)
        .put("predicted_low_minutes", result.lowMinutes)
        .put("predicted_high_minutes", result.highMinutes)
        .put("confidence", result.confidence.name)
        .put("live_sample_count", live?.sampleCount ?: 0)
        .put("historical_sample_count", historical?.sampleCount ?: 0)
        .put("data_gap", dataGap)
        .put("stale_live_data", staleLiveData)
        .toString()

    fun actualCall(
        identity: ForecastTelemetryIdentity,
        calledAtMillis: Long,
        lastInQueueAtMillis: Long,
        eventId: String = UUID.randomUUID().toString()
    ): String = base(identity, eventId, "actual_call")
        .put("called_at", Instant.ofEpochMilli(calledAtMillis).toString())
        .put("last_in_queue_at", Instant.ofEpochMilli(lastInQueueAtMillis).toString())
        .toString()
}

enum class ForecastTelemetrySendOutcome { SUCCESS, PERMANENT_FAILURE, RETRYABLE_FAILURE }

fun interface ForecastTelemetryTransport {
    fun send(payloadJson: String): ForecastTelemetrySendOutcome
}

class ForecastTelemetryHttpTransport(
    private val endpoint: String,
    private val publicAnonKey: String
) : ForecastTelemetryTransport {
    override fun send(payloadJson: String): ForecastTelemetrySendOutcome {
        // A missing deployment setting must not discard queued observations.
        if (endpoint.isBlank() || publicAnonKey.isBlank()) {
            return ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
        }
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            require(url.protocol == "https")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("apikey", publicAnonKey)
            connection.setRequestProperty("Authorization", "Bearer $publicAnonKey")
            val bytes = payloadJson.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            when (connection.responseCode) {
                200, 201 -> ForecastTelemetrySendOutcome.SUCCESS
                400, 413, 415, 422 -> ForecastTelemetrySendOutcome.PERMANENT_FAILURE
                else -> ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
            }
        } catch (_: Exception) {
            ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
        } finally {
            connection?.disconnect()
        }
    }
}

class ForecastTelemetryClient(
    private val store: ForecastTelemetryStore,
    private val transport: ForecastTelemetryTransport,
    private val onFailure: (String) -> Unit = {}
) {
    private val flushing = AtomicBoolean(false)

    fun enqueue(event: PendingForecastTelemetryEvent): Boolean = try {
        store.enqueue(event)
        true
    } catch (_: Exception) {
        reportFailure("queue_write_failed")
        false
    }

    /** Invoke on an IO job independent of queue tracking. Only one flush may run. */
    fun flushPending(nowMillis: Long, canContinue: () -> Boolean = { true }) {
        if (!flushing.compareAndSet(false, true)) return
        try {
            for (event in store.due(nowMillis)) {
                if (!canContinue()) break
                val outcome = try {
                    transport.send(event.payloadJson)
                } catch (_: Exception) {
                    ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
                }
                when (outcome) {
                    ForecastTelemetrySendOutcome.SUCCESS -> store.markSuccess(event.eventId)
                    ForecastTelemetrySendOutcome.PERMANENT_FAILURE -> {
                        store.markSuccess(event.eventId)
                        reportFailure("event_rejected")
                    }
                    ForecastTelemetrySendOutcome.RETRYABLE_FAILURE -> {
                        store.markFailure(event.eventId, nowMillis)
                        reportFailure("send_deferred")
                        // No burst of twenty requests when the backend is unavailable.
                        break
                    }
                }
            }
        } catch (_: Exception) {
            reportFailure("flush_deferred")
        } finally {
            flushing.set(false)
        }
    }

    private fun reportFailure(reason: String) {
        // Even diagnostics must never escape into tracking; never log a payload/key.
        try { onFailure(reason) } catch (_: Exception) { }
    }
}
