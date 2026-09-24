package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastTelemetryClientTest {

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
            values[key] = value
        }

        override fun remove(
            keys: Set<String>
        ) {
            keys.forEach {
                values.remove(it)
            }
        }
    }

    private class FakeTransport(
        private val outcome:
            ForecastTelemetrySendOutcome
    ) : ForecastTelemetryTransport {
        override fun send(
            payloadJson: String
        ): ForecastTelemetrySendOutcome =
            outcome
    }

    private fun identity() =
        ForecastTelemetryIdentity(
            installId =
                "11111111-1111-4111-8111-111111111111",
            forecastSessionId =
                "22222222-2222-4222-8222-222222222222",
            algorithmVersion =
                "v1",
            checkpointId =
                "98b5be92-d3a5-4ba2-9106-76eb4eb3df49",
            vehicleType =
                VehicleType.TRUCK,
            startedAtMillis =
                1_790_000_000_000L
        )

    @Test
    fun predictionPayloadContainsOnlyForecastFieldsAndNoPlateKeys() {
        val json =
            ForecastTelemetryPayloads
                .prediction(
                    identity =
                        identity(),
                    observedAtMillis =
                        1_790_000_060_000L,
                    currentPosition =
                        120,
                    queueCountSameType =
                        260,
                    historical =
                        HistoricalEstimate(
                            positionsPerHour = 18.4,
                            sampleCount = 72,
                            absoluteErrorP50Minutes = 24.0,
                            absoluteErrorP80Minutes = 52.0
                        ),
                    live =
                        SpeedEstimate(
                            positionsPerHour = 24.0,
                            sampleCount = 4,
                            newestSampleAtMillis = 1_790_000_050_000L
                        ),
                    result =
                        ForecastResult.Available(
                            etaMinutes = 343.0,
                            lowMinutes = 285.0,
                            highMinutes = 405.0,
                            confidence = ForecastConfidence.MEDIUM,
                            effectivePositionsPerHour = 20.8,
                            algorithmVersion = "v1"
                        ),
                    dataGap =
                        false,
                    staleLiveData =
                        false
                )
                .lowercase()

        assertTrue(
            json.contains(
                "\"event_type\":\"prediction\""
            )
        )
        assertTrue(
            json.contains(
                "\"algorithm_version\":\"v1\""
            )
        )
        assertTrue(
            json.contains(
                "\"current_position\":120"
            )
        )

        for (
            forbidden in
            listOf(
                "regnum",
                "registration_number",
                "\"plate\"",
                "device_id",
                "\"mac\""
            )
        ) {
            assertFalse(
                json.contains(
                    forbidden
                )
            )
        }
    }

    @Test
    fun actualCallPayloadHasNoVehicleNumberField() {
        val json =
            ForecastTelemetryPayloads
                .actualCall(
                    identity =
                        identity(),
                    calledAtMillis =
                        1_790_000_300_000L,
                    lastInQueueAtMillis =
                        1_790_000_280_000L
                )
                .lowercase()

        assertTrue(
            json.contains(
                "\"event_type\":\"actual_call\""
            )
        )
        assertFalse(
            json.contains(
                "regnum"
            )
        )
        assertFalse(
            json.contains(
                "registration_number"
            )
        )
    }

    @Test
    fun successRemovesPendingEvent() {
        val store =
            ForecastTelemetryStore(
                FakeStorage()
            )

        store.enqueue(
            PendingForecastTelemetryEvent(
                eventId = "e1",
                type = ForecastTelemetryEventType.PREDICTION,
                payloadJson = "{}",
                createdAtMillis = 1L,
                attemptCount = 0,
                nextAttemptAtMillis = 0L
            )
        )

        ForecastTelemetryClient(
            store = store,
            transport =
                FakeTransport(
                    ForecastTelemetrySendOutcome.SUCCESS
                )
        ).flushPending(
            nowMillis = 10L
        )

        assertTrue(
            store.all().isEmpty()
        )
    }

    @Test
    fun permanentFailureDropsEventButRetryableFailureKeepsIt() {
        val permanentStore =
            ForecastTelemetryStore(
                FakeStorage()
            )

        permanentStore.enqueue(
            PendingForecastTelemetryEvent(
                eventId = "permanent",
                type = ForecastTelemetryEventType.PREDICTION,
                payloadJson = "{}",
                createdAtMillis = 1L,
                attemptCount = 0,
                nextAttemptAtMillis = 0L
            )
        )

        ForecastTelemetryClient(
            store = permanentStore,
            transport =
                FakeTransport(
                    ForecastTelemetrySendOutcome.PERMANENT_FAILURE
                )
        ).flushPending(
            nowMillis = 1_000L
        )

        assertTrue(
            permanentStore.all().isEmpty()
        )

        val retryStore =
            ForecastTelemetryStore(
                FakeStorage()
            )

        retryStore.enqueue(
            PendingForecastTelemetryEvent(
                eventId = "retry",
                type = ForecastTelemetryEventType.PREDICTION,
                payloadJson = "{}",
                createdAtMillis = 1L,
                attemptCount = 0,
                nextAttemptAtMillis = 0L
            )
        )

        ForecastTelemetryClient(
            store = retryStore,
            transport =
                FakeTransport(
                    ForecastTelemetrySendOutcome.RETRYABLE_FAILURE
                )
        ).flushPending(
            nowMillis = 1_000L
        )

        val pending =
            retryStore.all()
                .single()

        assertEquals(
            1,
            pending.attemptCount
        )

        assertEquals(
            61_000L,
            pending.nextAttemptAtMillis
        )
    }

    @Test
    fun installationIdIsRandomOnceAndThenStable() {
        val storage =
            FakeStorage()

        var generated =
            0

        val ids =
            ForecastInstallationId(
                storage = storage,
                idFactory = {
                    generated += 1
                    "33333333-3333-4333-8333-33333333333$generated"
                }
            )

        val first =
            ids.getOrCreate()

        val second =
            ids.getOrCreate()

        assertEquals(
            first,
            second
        )

        assertEquals(
            1,
            generated
        )
    }
}
