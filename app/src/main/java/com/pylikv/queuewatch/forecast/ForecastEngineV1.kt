package com.pylikv.queuewatch.forecast

object ForecastEngineV1 {

    private const val LIVE_MAX_AGE_MS =
        60 * 60 * 1000L

    private const val MAX_LIVE_WEIGHT =
        0.55

    private const val MIN_RATE =
        0.25

    private const val MAX_RATE =
        200.0

    fun estimate(
        input: ForecastInput
    ): ForecastResult {

        val remaining =
            (input.currentPosition - 1)
                .coerceAtLeast(0)

        if (remaining == 0) {

            return ForecastResult.Available(
                etaMinutes = 0.0,
                lowMinutes = 0.0,
                highMinutes = 0.0,
                confidence =
                    ForecastConfidence.HIGH,
                effectivePositionsPerHour =
                    input.historical
                        ?.positionsPerHour
                        ?: input.live
                            ?.positionsPerHour
                        ?: 0.0
            )
        }

        val historicalRate =
            input.historical
                ?.positionsPerHour
                ?.takeIf {
                    it in
                        MIN_RATE..MAX_RATE
                }

        val liveFresh =
            input.live
                ?.takeIf {
                    it.positionsPerHour in
                        MIN_RATE..MAX_RATE &&
                        input.nowMillis -
                        it.newestSampleAtMillis <=
                        LIVE_MAX_AGE_MS
                }

        if (
            historicalRate == null &&
            liveFresh == null
        ) {

            return ForecastResult.Unavailable(
                reason =
                    "insufficient_speed_data"
            )
        }

        val liveWeight =
            when {

                liveFresh == null ->
                    0.0

                historicalRate == null ->
                    1.0

                liveFresh.sampleCount < 3 ->
                    0.15

                liveFresh.sampleCount < 6 ->
                    0.30

                else ->
                    MAX_LIVE_WEIGHT
            }

        val effective =
            when {

                historicalRate == null ->
                    liveFresh!!
                        .positionsPerHour

                liveFresh == null ->
                    historicalRate

                else ->
                    historicalRate *
                        (1.0 - liveWeight) +
                        liveFresh.positionsPerHour *
                        liveWeight
            }

        val eta =
            remaining *
                60.0 /
                effective

        val p80 =
            input.historical
                ?.absoluteErrorP80Minutes
                ?: (eta * 0.50)
                    .coerceAtLeast(30.0)

        val low =
            (eta - p80)
                .coerceAtLeast(0.0)

        val high =
            eta + p80

        val confidence =
            when {

                input.historical != null &&
                    input.historical
                        .sampleCount >= 50 &&
                    liveFresh != null &&
                    liveFresh.sampleCount >= 6 ->
                    ForecastConfidence.HIGH

                input.historical != null &&
                    input.historical
                        .sampleCount >= 15 ->
                    ForecastConfidence.MEDIUM

                else ->
                    ForecastConfidence.LOW
            }

        return ForecastResult.Available(
            etaMinutes = eta,
            lowMinutes = low,
            highMinutes = high,
            confidence = confidence,
            effectivePositionsPerHour =
                effective
        )
    }
}
