package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineV1Test {

    @Test
    fun noSpeedSourcesReturnsUnavailable() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 50,
                queueCount = 100,
                historical = null,
                live = null,
                nowMillis = 1_000_000L
            )
        )

        assertTrue(result is ForecastResult.Unavailable)
    }

    @Test
    fun positionOneDoesNotPromiseAnImmediateCallEvenWithHistory() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 1,
                queueCount = 100,
                historical = HistoricalEstimate(
                    positionsPerHour = 20.0,
                    sampleCount = 100,
                    absoluteErrorP50Minutes = 20.0,
                    absoluteErrorP80Minutes = 40.0
                ),
                live = null,
                nowMillis = 1_000_000L
            )
        )
        assertTrue(result is ForecastResult.Unavailable)
    }

    @Test
    fun positionOneRemainsUncertainWithNoSourcesOrOnlyLiveMovement() {
        for (live in listOf(null, SpeedEstimate(20.0, 10, 999_000L))) {
            val result = ForecastEngineV1.estimate(ForecastInput(
                currentPosition = 1, queueCount = 100, historical = null,
                live = live, nowMillis = 1_000_000L
            ))
            assertTrue(result is ForecastResult.Unavailable)
        }
    }

    @Test
    fun oneFreshLiveSampleCannotDominateHistory() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 101,
                queueCount = 150,
                historical = HistoricalEstimate(
                    positionsPerHour = 20.0,
                    sampleCount = 100,
                    absoluteErrorP50Minutes = 25.0,
                    absoluteErrorP80Minutes = 50.0
                ),
                live = SpeedEstimate(
                    positionsPerHour = 80.0,
                    sampleCount = 1,
                    newestSampleAtMillis = 999_000L
                ),
                nowMillis = 1_000_000L
            )
        ) as ForecastResult.Available

        assertTrue(result.effectivePositionsPerHour < 40.0)
    }

    @Test
    fun staleLiveEstimateIsIgnored() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 61,
                queueCount = 100,
                historical = HistoricalEstimate(
                    positionsPerHour = 20.0,
                    sampleCount = 100,
                    absoluteErrorP50Minutes = 20.0,
                    absoluteErrorP80Minutes = 45.0
                ),
                live = SpeedEstimate(
                    positionsPerHour = 100.0,
                    sampleCount = 10,
                    newestSampleAtMillis = 1_000L
                ),
                nowMillis = 4_000_000L
            )
        ) as ForecastResult.Available

        assertEquals(20.0, result.effectivePositionsPerHour, 0.001)
    }

    @Test
    fun currentPositionLargerThanQueueCountStillProducesForecast() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 120,
                queueCount = 100,
                historical = HistoricalEstimate(
                    positionsPerHour = 20.0,
                    sampleCount = 30,
                    absoluteErrorP50Minutes = 20.0,
                    absoluteErrorP80Minutes = 40.0
                ),
                live = null,
                nowMillis = 1_000_000L
            )
        )

        assertTrue(result is ForecastResult.Available)
    }

    @Test
    fun nonPositiveRatesAreRejected() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 30,
                queueCount = 40,
                historical = HistoricalEstimate(
                    positionsPerHour = -1.0,
                    sampleCount = 100,
                    absoluteErrorP50Minutes = 20.0,
                    absoluteErrorP80Minutes = 40.0
                ),
                live = SpeedEstimate(
                    positionsPerHour = 0.0,
                    sampleCount = 10,
                    newestSampleAtMillis = 999_000L
                ),
                nowMillis = 1_000_000L
            )
        )

        assertTrue(result is ForecastResult.Unavailable)
    }

    @Test
    fun liveOnlyForecastHasLowConfidence() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 31,
                queueCount = 50,
                historical = null,
                live = SpeedEstimate(
                    positionsPerHour = 20.0,
                    sampleCount = 10,
                    newestSampleAtMillis = 999_000L
                ),
                nowMillis = 1_000_000L
            )
        ) as ForecastResult.Available

        assertEquals(ForecastConfidence.LOW, result.confidence)
    }

    @Test
    fun forecastRangeAlwaysContainsEtaAndNeverGoesNegative() {
        val result = ForecastEngineV1.estimate(
            ForecastInput(
                currentPosition = 10,
                queueCount = 20,
                historical = HistoricalEstimate(
                    positionsPerHour = 30.0,
                    sampleCount = 40,
                    absoluteErrorP50Minutes = 15.0,
                    absoluteErrorP80Minutes = 90.0
                ),
                live = null,
                nowMillis = 1_000_000L
            )
        ) as ForecastResult.Available

        assertTrue(result.lowMinutes >= 0.0)
        assertTrue(result.lowMinutes <= result.etaMinutes)
        assertTrue(result.highMinutes >= result.etaMinutes)
    }
}
