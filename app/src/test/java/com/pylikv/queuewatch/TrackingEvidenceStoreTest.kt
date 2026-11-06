package com.pylikv.queuewatch

import com.pylikv.queuewatch.forecast.ForecastStorage
import org.junit.Assert.*
import org.junit.Test

class TrackingEvidenceStoreTest {
    private class Memory : ForecastStorage {
        val rows = mutableMapOf<String, String>()
        override fun read(key: String) = rows[key]
        override fun write(key: String, value: String) { rows[key] = value }
        override fun remove(keys: Set<String>) { keys.forEach(rows::remove) }
    }
    @Test fun movementIsCountedWithScreenClosedAndSurvivesServiceRestart() {
        val memory = Memory()
        TrackingEvidenceStore(memory).observe("visit", 1252, 1000)
        TrackingEvidenceStore(memory).observe("visit", 872, 16 * 3_600_000L)
        val evidence = TrackingEvidenceStore(memory).read("visit")!!
        assertEquals(380, evidence.positionsPassed)
        assertEquals(16 * 3_600_000L - 1000L, evidence.elapsedMillis)
    }
    @Test fun missingObservationsDoNotExtendMovementTimeOrInventProgress() {
        val store = TrackingEvidenceStore(Memory())
        val last = store.observe("visit", 872, 1000)
        assertEquals("STALE", displayedTrackingState("IN_QUEUE", last, 68 * 3_600_000L))
        assertEquals(0L, store.read("visit")!!.elapsedMillis)
        assertEquals("MISSING", displayedTrackingState("MISSING", last, 1001))
        assertEquals("SOURCE_ERROR", displayedTrackingState("SOURCE_ERROR", last, 1001))
    }
    @Test fun currentStateRequiresFreshConfirmationForTheSameVehicle() {
        val store = TrackingEvidenceStore(Memory())
        val evidence = store.observe("one", 150, 1000)
        assertEquals("IN_QUEUE", displayedTrackingState("IN_QUEUE", evidence, 121000))
        assertEquals("STALE", displayedTrackingState("IN_QUEUE", evidence, 121001))
        assertEquals("STALE", displayedTrackingState("IN_QUEUE", evidence, 999))
        assertNull(store.read("other"))
    }
    @Test fun aNewTrackingSessionDoesNotInheritThePreviousVehicle() {
        val store = TrackingEvidenceStore(Memory())
        store.observe("first", 1252, 1000)
        val other = store.observe("second", 10, 2000)
        assertEquals(0, other.positionsPassed)
        assertEquals(10, other.firstPosition)
        store.reset()
        assertNull(store.read("second"))
    }
}
