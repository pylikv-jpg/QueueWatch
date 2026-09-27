package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.junit.Assert.*
import org.junit.Test

class ElapsedWindowForecastTest {
    private class Memory : ForecastStorage {
        val map = mutableMapOf<String, String>()
        override fun read(key: String) = map[key]
        override fun write(key: String, value: String) { map[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(map::remove) }
    }
    private val identity = ForecastTelemetryIdentity("install", "session", "v1", "checkpoint", VehicleType.CAR, 0L)
    private fun run(engine: ElapsedWindowForecast, end: Int, position: (Int) -> Int): ShadowForecast {
        var result = ShadowForecast()
        for (m in 0..end) result = engine.observe(identity, m * 60_000L, position(m))
        return result
    }

    @Test fun tenCarsInFortyMinutesAndTwentyIdleMinutesGiveTenPerHour() {
        val result = run(ElapsedWindowForecast(Memory()), 60) { m -> 100 - when { m >= 40 -> 10; m >= 20 -> 5; else -> 0 } }
        assertEquals(10.0, result.positionsPerHour!!, 0.0001)
        assertEquals(540.0, result.predictedMinutes!!, 0.0001)
        assertEquals(60.0, result.windowMinutes!!, 0.0)
    }

    @Test fun warmupAndOneBurstCannotProduceANumericPrediction() {
        val engine = ElapsedWindowForecast(Memory())
        assertNull(run(engine, 59) { 100 - it / 10 }.predictedMinutes)
        val single = run(ElapsedWindowForecast(Memory()), 60) { if (it < 1) 100 else 80 }
        assertEquals("insufficient_movements", single.unavailableReason)
    }

    @Test fun ongoingStandstillLowersRateWithoutInventingAnEtaAtTheFront() {
        val memory = Memory()
        val engine = ElapsedWindowForecast(memory)
        run(engine, 60) { 100 - minOf(it, 20) }
        var result = ShadowForecast()
        for (m in 61..120) result = engine.observe(identity, m * 60_000L, 80)
        assertEquals(10.0, result.positionsPerHour!!, 0.0)
        assertEquals(480.0, result.predictedMinutes!!, 0.0)
        assertNull(engine.observe(identity, 121 * 60_000L, 1).predictedMinutes)
    }

    @Test fun rollingWindowKeepsOnlyObservedPastEndpoints() {
        val result = run(ElapsedWindowForecast(Memory()), 180) { 1000 - it }
        assertEquals(120.0, result.windowMinutes!!, 0.0)
        assertEquals(120, result.movedPositions)
        assertEquals(820.0, result.predictedMinutes!!, 0.0)
    }

    @Test fun restartKeepsHistoryButGapBackwardsMoveAndTypeChangeResetIt() {
        val memory = Memory()
        run(ElapsedWindowForecast(memory), 60) { 200 - it }
        val restored = ElapsedWindowForecast(memory)
        assertNotNull(restored.observe(identity, 61 * 60_000L, 139).predictedMinutes)
        assertNull(restored.observe(identity, 70 * 60_000L, 130).predictedMinutes)
        assertEquals(0, restored.observe(identity, 71 * 60_000L, 140).movedPositions)
        assertEquals(0.0, restored.observe(identity.copy(vehicleType = VehicleType.TRUCK), 72 * 60_000L, 100).windowMinutes!!, 0.0)
    }

    @Test fun missingPollClearsHistoryAndDuplicateTimestampDoesNotAppend() {
        val engine = ElapsedWindowForecast(Memory())
        run(engine, 60) { 200 - it }
        assertEquals("non_increasing_time", engine.observe(identity, 60 * 60_000L, 100).unavailableReason)
        engine.observe(identity, 61 * 60_000L, null)
        assertNull(engine.observe(identity, 62 * 60_000L, 130).predictedMinutes)
    }
}
