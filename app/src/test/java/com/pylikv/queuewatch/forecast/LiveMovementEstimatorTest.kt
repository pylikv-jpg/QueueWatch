package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMovementEstimatorTest {

    @Test
    fun firstBurstUsesVirtualWindowInsteadOfBurstDuration() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 100)
        estimator.observePosition(10 * MINUTE, 90)

        val speed = estimator.estimate(
            now = 10 * MINUTE,
            virtualWindowMinutes = 40.0,
            minimumObservedMinutes = 0.0
        )

        assertEquals(15.0, speed!!.positionsPerHour, 0.001)
    }

    @Test
    fun liveOnlyForecastWaitsForRealWarmup() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 100)
        estimator.observePosition(10 * MINUTE, 90)

        assertNull(
            estimator.estimate(
                now = 10 * MINUTE,
                virtualWindowMinutes = 40.0,
                minimumObservedMinutes = 30.0
            )
        )
    }

    @Test
    fun realElapsedTimeReplacesVirtualWindow() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 100)
        estimator.observePosition(20 * MINUTE, 90)
        estimator.observePosition(80 * MINUTE, 80)

        val speed = estimator.estimate(
            now = 80 * MINUTE,
            virtualWindowMinutes = 40.0,
            minimumObservedMinutes = 30.0
        )

        assertTrue(speed != null)
        assertTrue(speed!!.positionsPerHour < 20.0)
        assertTrue(speed.positionsPerHour > 10.0)
    }

    @Test
    fun stationaryQueueIsDetectedFromElapsedTime() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 50)
        estimator.observePosition(10 * MINUTE, 50)
        estimator.observePosition(21 * MINUTE, 50)

        assertTrue(estimator.stationary(21 * MINUTE))
    }

    @Test
    fun longGapResetsLiveEstimate() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 100)
        estimator.observePosition(10 * MINUTE, 90)
        estimator.observePosition(25 * MINUTE, 80)

        assertNull(
            estimator.estimate(
                now = 25 * MINUTE,
                virtualWindowMinutes = 40.0,
                minimumObservedMinutes = 0.0
            )
        )
        assertTrue(estimator.dataGap)
    }

    @Test
    fun slowerRecentHourPullsSpeedDown() {
        val estimator = LiveMovementEstimator()
        estimator.observePosition(0L, 100)
        estimator.observePosition(10 * MINUTE, 95)
        estimator.observePosition(40 * MINUTE, 80)
        estimator.observePosition(70 * MINUTE, 80)

        val speed = estimator.estimate(
            now = 70 * MINUTE,
            virtualWindowMinutes = 40.0,
            minimumObservedMinutes = 30.0
        )!!

        val longRate = 20.0 * 60.0 / 70.0
        assertTrue(speed.positionsPerHour < longRate)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
