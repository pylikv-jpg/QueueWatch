package com.pylikv.queuewatch.forecast

import java.nio.charset.StandardCharsets
import java.util.Base64

enum class ForecastTelemetryEventType {
    PREDICTION,
    ACTUAL_CALL
}

data class PendingForecastTelemetryEvent(
    val eventId: String,
    val type:
        ForecastTelemetryEventType,
    val payloadJson: String,
    val createdAtMillis: Long,
    val attemptCount: Int,
    val nextAttemptAtMillis: Long
)

class ForecastTelemetryStore(
    private val storage:
        ForecastStorage,
    private val maxEvents:
        Int = 500
) {

    companion object {
        private const val KEY_QUEUE =
            "forecast_telemetry_queue"

        private val RETRY_DELAYS_MS =
            longArrayOf(
                60_000L,
                5 * 60_000L,
                15 * 60_000L,
                60 * 60_000L,
                6 * 60 * 60_000L
            )
    }

    init {
        require(
            maxEvents >
                0
        )
    }

    @Synchronized
    fun enqueue(
        event:
            PendingForecastTelemetryEvent
    ) {
        val events =
            all()
                .filterNot {
                    it.eventId ==
                        event.eventId
                }
                .toMutableList()

        events.add(
            event
        )

        trimToCap(
            events
        )

        persist(
            events
        )
    }

    @Synchronized
    fun all():
        List<PendingForecastTelemetryEvent> =
        readQueue()
            .sortedBy {
                it.createdAtMillis
            }

    @Synchronized
    fun due(
        nowMillis: Long,
        limit: Int = 20
    ): List<PendingForecastTelemetryEvent> =
        all()
            .asSequence()
            .filter {
                it.nextAttemptAtMillis <=
                    nowMillis
            }
            .take(
                limit.coerceAtLeast(
                    0
                )
            )
            .toList()

    @Synchronized
    fun markSuccess(
        eventId: String
    ) {
        val events =
            all()
                .filterNot {
                    it.eventId ==
                        eventId
                }

        persist(
            events
        )
    }

    @Synchronized
    fun markFailure(
        eventId: String,
        nowMillis: Long
    ) {
        val events =
            all()
                .map {
                    if (
                        it.eventId !=
                        eventId
                    ) {
                        it
                    } else {
                        val newAttemptCount =
                            it.attemptCount +
                                1

                        val delay =
                            retryDelayForAttempt(
                                newAttemptCount
                            )

                        it.copy(
                            attemptCount =
                                newAttemptCount,
                            nextAttemptAtMillis =
                                nowMillis +
                                    delay
                        )
                    }
                }

        persist(
            events
        )
    }

    private fun trimToCap(
        events:
            MutableList<
                PendingForecastTelemetryEvent
            >
    ) {
        while (
            events.size >
            maxEvents
        ) {
            val predictionIndex =
                events
                    .withIndex()
                    .filter {
                        it.value.type ==
                            ForecastTelemetryEventType
                                .PREDICTION
                    }
                    .minByOrNull {
                        it.value
                            .createdAtMillis
                    }
                    ?.index

            val removeIndex =
                predictionIndex
                    ?: events
                        .withIndex()
                        .minByOrNull {
                            it.value
                                .createdAtMillis
                        }
                        ?.index
                    ?: return

            events.removeAt(
                removeIndex
            )
        }
    }

    private fun retryDelayForAttempt(
        attemptCount: Int
    ): Long {
        val index =
            (
                attemptCount -
                    1
                )
                .coerceIn(
                    0,
                    RETRY_DELAYS_MS
                        .lastIndex
                )

        return RETRY_DELAYS_MS[
            index
        ]
    }

    private fun readQueue():
        List<PendingForecastTelemetryEvent> {
        val encoded =
            storage.read(
                KEY_QUEUE
            )
                .orEmpty()

        if (
            encoded.isBlank()
        ) {
            return emptyList()
        }

        return encoded
            .lineSequence()
            .mapNotNull {
                decode(
                    it
                )
            }
            .toList()
    }

    private fun persist(
        events:
            List<PendingForecastTelemetryEvent>
    ) {
        if (
            events.isEmpty()
        ) {
            storage.remove(
                setOf(
                    KEY_QUEUE
                )
            )
            return
        }

        storage.write(
            KEY_QUEUE,
            events.joinToString(
                separator = "\n"
            ) {
                encode(
                    it
                )
            }
        )
    }

    private fun encode(
        event:
            PendingForecastTelemetryEvent
    ): String {
        val payload =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    event.payloadJson
                        .toByteArray(
                            StandardCharsets.UTF_8
                        )
                )

        return listOf(
            event.eventId,
            event.type.name,
            event.createdAtMillis
                .toString(),
            event.attemptCount
                .toString(),
            event.nextAttemptAtMillis
                .toString(),
            payload
        )
            .joinToString(
                separator = "|"
            )
    }

    private fun decode(
        line: String
    ): PendingForecastTelemetryEvent? {
        val parts =
            line.split(
                "|",
                limit = 6
            )

        if (
            parts.size !=
            6
        ) {
            return null
        }

        return try {
            PendingForecastTelemetryEvent(
                eventId =
                    parts[0],
                type =
                    ForecastTelemetryEventType
                        .valueOf(
                            parts[1]
                        ),
                createdAtMillis =
                    parts[2]
                        .toLong(),
                attemptCount =
                    parts[3]
                        .toInt(),
                nextAttemptAtMillis =
                    parts[4]
                        .toLong(),
                payloadJson =
                    String(
                        Base64.getUrlDecoder()
                            .decode(
                                parts[5]
                            ),
                        StandardCharsets.UTF_8
                    )
            )
        } catch (
            _: Exception
        ) {
            null
        }
    }
}
