package com.pylikv.queuewatch.forecast

import org.junit.Assert.*
import org.junit.Test

class LiveMovementEstimatorTest {
    @Test fun batchCountsOnceAndArrivalsDoNotChangeRate() {
        val estimator = LiveMovementEstimator()
        estimator.observePositions(0, mapOf("A" to 120, "B" to 121, "C" to 122))
        estimator.observePositions(600_000, mapOf("A" to 110, "B" to 111, "C" to 112, "NEW" to 200))
        val result = estimator.estimate(600_000)!!
        assertEquals(60.0, result.positionsPerHour, 0.001)
        assertEquals(1, result.sampleCount)
    }
    @Test fun waitingTimeReducesRateInsteadOfBeingDiscarded() {
        val e = LiveMovementEstimator()
        e.observePositions(0, mapOf("A" to 100))
        for (i in 1..10) e.observePositions(i * 60_000L, mapOf("A" to if (i < 10) 100 else 90))
        assertEquals(60.0, e.estimate(600_000)!!.positionsPerHour, 0.001)
        for (i in 11..20) e.observePositions(i * 60_000L, mapOf("A" to 90))
        assertEquals(30.0, e.estimate(1_200_000)!!.positionsPerHour, 0.001)
    }
    @Test fun gapsAndBackwardsMovementDoNotBecomeThroughput() {
        val e = LiveMovementEstimator()
        e.observePositions(0, mapOf("A" to 20))
        e.observePositions(600_000, mapOf("A" to 22))
        assertNull(e.estimate(600_000))
        e.observePositions(3_600_000, mapOf("A" to 1))
        assertNull(e.estimate(3_600_000))
        assertTrue(e.dataGap)
    }
    @Test fun duplicateTimestampAndStaleDataCannotCreateSpeed() {
        val e = LiveMovementEstimator()
        e.observePositions(0, mapOf("A" to 20))
        e.observePositions(0, mapOf("A" to 10))
        assertNull(e.estimate(0))
        e.observePositions(600_000, mapOf("A" to 10))
        assertNull(e.estimate(900_000))
    }
    @Test fun stationaryQueueIsExplicit() {
        val e = LiveMovementEstimator()
        for (i in 0..30) e.observePositions(i * 60_000L, mapOf("A" to 20))
        assertTrue(e.stationary(1_800_000))
        assertNull(e.estimate(1_800_000))
    }


    @Test
    fun massRenumberingCountsAsOneBatchMovement() {
        val estimator = LiveMovementEstimator()

        estimator.observePositions(
            0L,
            mapOf("A" to 120, "B" to 121, "C" to 122)
        )
        estimator.observePositions(
            10 * 60_000L,
            mapOf("A" to 110, "B" to 111, "C" to 112)
        )

        val estimate =
            estimator.estimate(10 * 60_000L)!!

        assertEquals(
            60.0,
            estimate.positionsPerHour,
            0.01
        )
        assertEquals(
            1,
            estimate.sampleCount
        )
    }

    @Test
    fun arrivalsBehindQueueDoNotReduceMeasuredThroughput() {
        val estimator = LiveMovementEstimator()

        estimator.observePositions(
            0L,
            mapOf("A" to 20, "B" to 21)
        )
        estimator.observePositions(
            10 * 60_000L,
            mapOf(
                "A" to 15,
                "B" to 16,
                "NEW" to 40
            )
        )

        val estimate =
            estimator.estimate(10 * 60_000L)!!

        assertEquals(
            30.0,
            estimate.positionsPerHour,
            0.01
        )
    }

    @Test
    fun backwardsMovementIsNotThroughput() {
        val estimator = LiveMovementEstimator()

        estimator.observePositions(
            0L,
            mapOf("A" to 20)
        )
        estimator.observePositions(
            10 * 60_000L,
            mapOf("A" to 22)
        )

        assertNull(
            estimator.estimate(
                10 * 60_000L
            )
        )
    }

    @Test
    fun oldSamplesFallOutOfSixtyMinuteWindow() {
        val estimator = LiveMovementEstimator()

        estimator.observePositions(
            0L,
            mapOf("A" to 20)
        )
        estimator.observePositions(
            10 * 60_000L,
            mapOf("A" to 15)
        )

        assertNull(
            estimator.estimate(
                71 * 60_000L
            )
        )
    }

    @Test
    fun independentEstimatorsDoNotShareVehicleTypeSamples() {
        val cars =
            LiveMovementEstimator()
        val trucks =
            LiveMovementEstimator()

        cars.observePositions(
            0L,
            mapOf("C1" to 20)
        )
        trucks.observePositions(
            0L,
            mapOf("T1" to 30)
        )

        cars.observePositions(
            10 * 60_000L,
            mapOf("C1" to 20)
        )
        trucks.observePositions(
            10 * 60_000L,
            mapOf("T1" to 25)
        )

        assertNull(
            cars.estimate(
                10 * 60_000L
            )
        )

        assertEquals(
            30.0,
            trucks.estimate(
                10 * 60_000L
            )!!.positionsPerHour,
            0.01
        )
    }

}
