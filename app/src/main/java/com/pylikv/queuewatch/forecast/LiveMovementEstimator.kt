package com.pylikv.queuewatch.forecast

/**
 * Estimates short-term queue throughput from consecutive queue snapshots.
 *
 * One transition between snapshots produces at most one speed sample,
 * regardless of how many vehicles were renumbered in the same batch.
 */
class LiveMovementEstimator {

    companion object {
        private const val WINDOW_MS =
            60 * 60 * 1000L

        private const val MIN_RATE =
            0.25

        private const val MAX_RATE =
            200.0
    }

    private data class MovementSample(
        val timestampMillis: Long,
        val positionsPerHour: Double
    )

    private var previousTimestampMillis: Long? =
        null

    private var previousPositions:
        Map<String, Int> =
        emptyMap()

    private val samples =
        mutableListOf<MovementSample>()

    fun observePositions(
        timestampMillis: Long,
        positions: Map<String, Int>
    ) {
        val previousTimestamp =
            previousTimestampMillis

        if (previousTimestamp == null) {
            previousTimestampMillis =
                timestampMillis

            previousPositions =
                positions.toMap()

            return
        }

        if (timestampMillis <= previousTimestamp) {
            return
        }

        val elapsedMillis =
            timestampMillis -
                previousTimestamp

        val positiveDeltas =
            previousPositions
                .mapNotNull { (vehicleId, previousPosition) ->
                    val currentPosition =
                        positions[vehicleId]
                            ?: return@mapNotNull null

                    (previousPosition - currentPosition)
                        .takeIf { it > 0 }
                        ?.toDouble()
                }

        if (positiveDeltas.isNotEmpty()) {
            val movedPositions =
                median(
                    positiveDeltas
                )

            val elapsedHours =
                elapsedMillis /
                    3_600_000.0

            val rate =
                movedPositions /
                    elapsedHours

            if (
                rate in
                    MIN_RATE..MAX_RATE
            ) {
                samples.add(
                    MovementSample(
                        timestampMillis =
                            timestampMillis,

                        positionsPerHour =
                            rate
                    )
                )
            }
        }

        previousTimestampMillis =
            timestampMillis

        previousPositions =
            positions.toMap()

        prune(
            timestampMillis
        )
    }

    fun estimate(
        nowMillis: Long
    ): SpeedEstimate? {
        prune(
            nowMillis
        )

        if (samples.isEmpty()) {
            return null
        }

        return SpeedEstimate(
            positionsPerHour =
                median(
                    samples.map {
                        it.positionsPerHour
                    }
                ),

            sampleCount =
                samples.size,

            newestSampleAtMillis =
                samples.maxOf {
                    it.timestampMillis
                }
        )
    }

    private fun prune(
        nowMillis: Long
    ) {
        val cutoff =
            nowMillis -
                WINDOW_MS

        samples.removeAll {
            it.timestampMillis <
                cutoff
        }
    }

    private fun median(
        values: List<Double>
    ): Double {
        val sorted =
            values.sorted()

        val middle =
            sorted.size /
                2

        return if (
            sorted.size % 2 == 0
        ) {
            (
                sorted[middle - 1] +
                    sorted[middle]
                ) / 2.0
        } else {
            sorted[middle]
        }
    }
}
