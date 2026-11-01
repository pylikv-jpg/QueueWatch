package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForecastAnalyticsSessionTest {
    private class Memory : ForecastStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }
    private val history = HistoricalEstimate(10.0, 72, 10.0, 30.0)

    @Test fun bothModelsAndOneActualCallSurviveProcessRecreationWithoutSendingTheVehicleNumber() {
        val storage = Memory(); val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { ForecastTelemetrySendOutcome.SUCCESS })
        val session = ForecastAnalyticsSession(storage, client)
        val localCarKey = "LOCAL_ONLY_PLATE|checkpoint"
        for (minute in 0..61) {
            val position = 100 - minute / 10
            session.observe(localCarKey, "checkpoint", VehicleType.TRUCK, minute * 60_000L,
                position, 140, mapOf("LOCAL_ONLY_PLATE" to position, "OTHER_LOCAL_PLATE" to position + 1), history)
        }
        val observations = queue.all().filter { it.type == ForecastTelemetryEventType.OBSERVATION }
            .map { JSONObject(it.payloadJson) }
        assertNotNull(observations.last().opt("baseline_predicted_minutes"))
        assertFalse(observations.last().isNull("shadow_predicted_minutes"))
        assertTrue(queue.all().none { it.payloadJson.contains("LOCAL_ONLY_PLATE") })
        ForecastAnalyticsSession(storage, client).called(localCarKey, "checkpoint", 61 * 60_000L + 20_000L)
        ForecastAnalyticsSession(storage, client).called(localCarKey, "checkpoint", 61 * 60_000L + 40_000L)
        val calls = queue.all().filter { it.type == ForecastTelemetryEventType.ACTUAL_CALL }
        assertEquals(1, calls.size)
        assertEquals("1970-01-01T01:01:00Z", JSONObject(calls.single().payloadJson).getString("last_in_queue_at"))
    }

    @Test fun noQueueObservationMeansNoInventedActualCallOrInstallationIdentity() {
        val storage = Memory(); val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { ForecastTelemetrySendOutcome.SUCCESS })
        ForecastAnalyticsSession(storage, client).called("unknown-car", "checkpoint", 60_000L)
        assertTrue(queue.all().isEmpty())
        assertNull(storage.read("forecast_install_id"))
    }

    @Test fun everyTwentySecondPollIsCalculatedButRegularTelemetryIsLimitedToOncePerMinute() {
        val storage = Memory(); val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { ForecastTelemetrySendOutcome.SUCCESS })
        val session = ForecastAnalyticsSession(storage, client)
        for (second in 0..120 step 20) session.observe("car", "checkpoint", VehicleType.CAR,
            second * 1000L, 100 - second / 20, 110, mapOf("local" to 100 - second / 20), history)
        assertEquals(3, queue.all().count { it.type == ForecastTelemetryEventType.OBSERVATION })
        assertEquals(3, queue.all().count { it.type == ForecastTelemetryEventType.PREDICTION })
        session.unavailable("car", "checkpoint", 130_000L, "SOURCE_ERROR")
        assertEquals("SOURCE_ERROR", JSONObject(queue.all().last().payloadJson).getString("observation_state"))
    }
}
