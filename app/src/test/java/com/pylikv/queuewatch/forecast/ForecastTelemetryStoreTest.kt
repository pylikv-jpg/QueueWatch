package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTelemetryStoreTest {

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
    }

    private fun event(
        id: String,
        type:
            ForecastTelemetryEventType,
        createdAt: Long
    ) =
        PendingForecastTelemetryEvent(
            eventId = id,
            type = type,
            payloadJson =
                "{\"event_id\":\"$id\"}",
            createdAtMillis =
                createdAt,
            attemptCount =
                0,
            nextAttemptAtMillis =
                0L
        )

    @Test
    fun queueCapDropsOldestPredictionBeforeActualCall() {
        val store =
            ForecastTelemetryStore(
                storage =
                    FakeStorage(),
                maxEvents =
                    3
            )

        store.enqueue(
            event(
                "call",
                ForecastTelemetryEventType.ACTUAL_CALL,
                1L
            )
        )

        store.enqueue(
            event(
                "p1",
                ForecastTelemetryEventType.PREDICTION,
                2L
            )
        )

        store.enqueue(
            event(
                "p2",
                ForecastTelemetryEventType.PREDICTION,
                3L
            )
        )

        store.enqueue(
            event(
                "p3",
                ForecastTelemetryEventType.PREDICTION,
                4L
            )
        )

        val all =
            store.all()

        assertEquals(
            3,
            all.size
        )

        assertTrue(
            all.any {
                it.eventId ==
                    "call"
            }
        )

        assertFalse(
            all.any {
                it.eventId ==
                    "p1"
            }
        )
    }

    @Test
    fun successfulSendRemovesEvent() {
        val store =
            ForecastTelemetryStore(
                FakeStorage()
            )

        store.enqueue(
            event(
                "p1",
                ForecastTelemetryEventType.PREDICTION,
                1L
            )
        )

        store.markSuccess(
            "p1"
        )

        assertTrue(
            store.all()
                .isEmpty()
        )
    }

    @Test
    fun retryBackoffStartsAtOneMinuteThenFiveMinutes() {
        val store =
            ForecastTelemetryStore(
                FakeStorage()
            )

        store.enqueue(
            event(
                "p1",
                ForecastTelemetryEventType.PREDICTION,
                1L
            )
        )

        store.markFailure(
            eventId =
                "p1",
            nowMillis =
                1_000L
        )

        var pending =
            store.all()
                .single()

        assertEquals(
            1,
            pending.attemptCount
        )

        assertEquals(
            61_000L,
            pending.nextAttemptAtMillis
        )

        store.markFailure(
            eventId =
                "p1",
            nowMillis =
                100_000L
        )

        pending =
            store.all()
                .single()

        assertEquals(
            2,
            pending.attemptCount
        )

        assertEquals(
            400_000L,
            pending.nextAttemptAtMillis
        )
    }

    @Test
    fun dueReturnsOnlyEventsWhoseBackoffExpired() {
        val storage =
            FakeStorage()

        val store =
            ForecastTelemetryStore(
                storage
            )

        store.enqueue(
            event(
                "p1",
                ForecastTelemetryEventType.PREDICTION,
                1L
            )
        )

        store.enqueue(
            event(
                "p2",
                ForecastTelemetryEventType.PREDICTION,
                2L
            )
        )

        store.markFailure(
            eventId =
                "p2",
            nowMillis =
                1_000L
        )

        val due =
            store.due(
                nowMillis =
                    30_000L
            )

        assertEquals(
            listOf(
                "p1"
            ),
            due.map {
                it.eventId
            }
        )
    }
}
