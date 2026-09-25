package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastSessionStoreTest {

    @Test
    fun repeatedCalledSnapshotsPreserveFirstConfirmationTime() {
        val store = ForecastSessionStore(FakeStorage())
        store.ensureSession("AA111|checkpoint", 1_000L)
        store.markInQueue("AA111|checkpoint", 2_000L)
        store.markCalled("AA111|checkpoint", 3_000L)
        store.markCalled("AA111|checkpoint", 5_000L)
        assertEquals(3_000L, store.calledAtMillis("AA111|checkpoint"))
    }

    @Test
    fun firstForecastObservationDefinesSessionStartTime() {
        val store = ForecastSessionStore(FakeStorage())
        store.saveAvailable("AA111|checkpoint", ForecastResult.Available(
            10.0, 5.0, 15.0, ForecastConfidence.LOW, 6.0), 60_000L)
        assertEquals(60_000L, store.startedAtMillis("AA111|checkpoint"))
    }

    private class FakeStorage :
        ForecastStorage {

        private val values =
            mutableMapOf<String, String>()

        override fun read(
            key: String
        ): String? =
            values[key]

        override fun write(
            key: String,
            value: String
        ) {
            values[key] =
                value
        }

        override fun remove(
            keys: Set<String>
        ) {
            keys.forEach {
                values.remove(
                    it
                )
            }
        }

        fun contains(
            key: String
        ): Boolean =
            values.containsKey(
                key
            )
    }

    @Test
    fun sameLocalCarKeyKeepsSameForecastSession() {
        val storage =
            FakeStorage()

        var generated =
            0

        val store =
            ForecastSessionStore(
                storage = storage,
                sessionIdFactory = {
                    generated += 1
                    "session-$generated"
                }
            )

        val first =
            store.ensureSession(
                "AA1234|checkpoint-a"
            )

        val second =
            store.ensureSession(
                "AA1234|checkpoint-a"
            )

        assertEquals(
            "session-1",
            first
        )
        assertEquals(
            first,
            second
        )
        assertEquals(
            1,
            generated
        )
    }

    @Test
    fun differentLocalCarKeyCreatesNewSessionAndClearsStaleForecast() {
        val storage =
            FakeStorage()

        var generated =
            0

        val store =
            ForecastSessionStore(
                storage = storage,
                sessionIdFactory = {
                    generated += 1
                    "session-$generated"
                }
            )

        store.ensureSession(
            "AA1234|checkpoint-a"
        )

        store.saveAvailable(
            localCarKey =
                "AA1234|checkpoint-a",
            result =
                ForecastResult.Available(
                    etaMinutes = 120.0,
                    lowMinutes = 90.0,
                    highMinutes = 150.0,
                    confidence =
                        ForecastConfidence.MEDIUM,
                    effectivePositionsPerHour =
                        20.0
                ),
            updatedAtMillis =
                1_000L
        )

        assertNull(
            store.read(
                "BB5678|checkpoint-b"
            )
        )

        val newSession =
            store.ensureSession(
                "BB5678|checkpoint-b"
            )

        assertEquals(
            "session-2",
            newSession
        )

        assertNull(
            store.read(
                "AA1234|checkpoint-a"
            )
        )

        assertTrue(
            !storage.contains(
                ForecastSessionStore.KEY_ETA_MINUTES
            )
        )
    }

    @Test
    fun calledCompletionKeepsSessionIdButClearsVisibleEta() {
        val storage =
            FakeStorage()

        val store =
            ForecastSessionStore(
                storage = storage,
                sessionIdFactory = {
                    "session-called"
                }
            )

        store.ensureSession(
            "AA1234|checkpoint-a"
        )

        store.saveAvailable(
            localCarKey =
                "AA1234|checkpoint-a",
            result =
                ForecastResult.Available(
                    etaMinutes = 20.0,
                    lowMinutes = 10.0,
                    highMinutes = 35.0,
                    confidence =
                        ForecastConfidence.HIGH,
                    effectivePositionsPerHour =
                        25.0
                ),
            updatedAtMillis =
                2_000L
        )

        store.markCalled(
            localCarKey =
                "AA1234|checkpoint-a",
            calledAtMillis =
                3_000L
        )

        assertNull(
            store.read(
                "AA1234|checkpoint-a"
            )
        )

        assertEquals(
            "session-called",
            store.currentSessionId(
                "AA1234|checkpoint-a"
            )
        )

        assertEquals(
            3_000L,
            store.calledAtMillis(
                "AA1234|checkpoint-a"
            )
        )
    }

    @Test
    fun sessionStartAndLastInQueueTimesRemainAvailableAfterCall() {
        val storage =
            FakeStorage()

        val store =
            ForecastSessionStore(
                storage = storage,
                sessionIdFactory = {
                    "session-timing"
                }
            )

        store.ensureSession(
            localCarKey =
                "AA1234|checkpoint-a",
            startedAtMillis =
                1_000L
        )

        store.markInQueue(
            localCarKey =
                "AA1234|checkpoint-a",
            observedAtMillis =
                2_000L
        )

        store.markInQueue(
            localCarKey =
                "AA1234|checkpoint-a",
            observedAtMillis =
                3_000L
        )

        store.markCalled(
            localCarKey =
                "AA1234|checkpoint-a",
            calledAtMillis =
                4_000L
        )

        assertEquals(
            1_000L,
            store.startedAtMillis(
                "AA1234|checkpoint-a"
            )
        )

        assertEquals(
            3_000L,
            store.lastInQueueAtMillis(
                "AA1234|checkpoint-a"
            )
        )

        assertEquals(
            4_000L,
            store.calledAtMillis(
                "AA1234|checkpoint-a"
            )
        )
    }
}
