package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForecastTelemetryRecorderTest {
    private class MemoryStorage : ForecastStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(values::remove) }
    }
    private val identity = ForecastTelemetryIdentity(
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222", "v1",
        "98b5be92-d3a5-4ba2-9106-76eb4eb3df49", VehicleType.TRUCK, 0L
    )
    private val forecast = ForecastResult.Available(100.0, 70.0, 130.0,
        ForecastConfidence.LOW, 30.0)
    private fun record(recorder: ForecastTelemetryRecorder, at: Long, position: Int = 51,
        result: ForecastResult.Available = forecast) = recorder.recordPrediction(
        identity, at, position, 100, null, null, result, false, false
    )

    @Test fun unchangedPredictionsAreSuppressedAcrossRestartButMaterialChangesAreKept() {
        val storage = MemoryStorage()
        val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { error("offline") })
        record(ForecastTelemetryRecorder(storage, client), 60_000L)
        val restored = ForecastTelemetryRecorder(storage, client)
        record(restored, 80_000L)
        assertEquals(1, queue.all().size)
        record(restored, 100_000L, position = 50)
        record(restored, 120_000L, position = 50, result = forecast.copy(etaMinutes = 111.0))
        record(restored, 140_000L, position = 50, result = forecast.copy(etaMinutes = 111.0, confidence = ForecastConfidence.MEDIUM))
        assertEquals(4, queue.all().size)
        record(restored, 1_040_000L, position = 50, result = forecast.copy(etaMinutes = 111.0, confidence = ForecastConfidence.MEDIUM))
        assertEquals(5, queue.all().size)
    }

    @Test fun confirmedCallIsQueuedOnceAcrossRestartAndCarriesLastObservedQueueTime() {
        val storage = MemoryStorage()
        val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { ForecastTelemetrySendOutcome.SUCCESS })
        val recorder = ForecastTelemetryRecorder(storage, client)
        record(recorder, 60_000L)
        assertTrue(recorder.recordActualCall(identity, 120_000L, 100_000L))
        val call = JSONObject(queue.all().last().payloadJson)
        assertEquals("1970-01-01T00:02:00Z", call.getString("called_at"))
        assertEquals("1970-01-01T00:01:40Z", call.getString("last_in_queue_at"))
        assertEquals(10, call.length())
        client.flushPending(150_000L)
        assertTrue(queue.all().isEmpty())
        assertFalse(ForecastTelemetryRecorder(storage, client).recordActualCall(identity, 180_000L, 100_000L))
        assertTrue(queue.all().isEmpty())
    }

    @Test fun invalidCallTimingAndPredictionAfterCallAreNeverQueued() {
        val storage = MemoryStorage()
        val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { ForecastTelemetrySendOutcome.SUCCESS })
        val recorder = ForecastTelemetryRecorder(storage, client)
        assertFalse(recorder.recordActualCall(identity, 30_000L, 60_000L))
        assertTrue(queue.all().isEmpty())
        recorder.recordActualCall(identity, 100_000L, 60_000L)
        record(recorder, 120_000L)
        assertEquals(1, queue.all().size)
    }

    @Test fun offlineTransportDoesNotPreventNextTrackingObservationAndRetriesIdenticalPayload() {
        val storage = MemoryStorage()
        val queue = ForecastTelemetryStore(storage)
        val client = ForecastTelemetryClient(queue, ForecastTelemetryTransport { throw java.io.IOException("offline") })
        val recorder = ForecastTelemetryRecorder(storage, client)
        record(recorder, 60_000L)
        val original = queue.all().single().payloadJson
        client.flushPending(60_000L)
        record(recorder, 80_000L, position = 49)
        assertEquals(2, queue.all().size)
        assertEquals(original, queue.all().first().payloadJson)
        assertEquals(1, queue.all().first().attemptCount)
        assertEquals(120_000L, queue.all().first().nextAttemptAtMillis)
    }
}
