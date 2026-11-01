package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveMovementEstimatorTest {

    @Test
    fun stationaryPollsAreIncludedInElapsedTimeBeforeBatchMoves() {
        val estimator = LiveMovementEstimator()
        estimator.observePositions(0L, mapOf("A" to 20, "B" to 21))
        for (at in 20_000L until 600_000L step 20_000L) {
            estimator.observePositions(at, mapOf("A" to 20, "B" to 21))
        }
        estimator.observePositions(600_000L, mapOf("A" to 15, "B" to 16))
        assertEquals(30.0, estimator.estimate(600_000L)!!.positionsPerHour, 0.01)
    }

    @Test
    fun twentySecondPollingDoesNotTurnOneCarInTenMinutesInto180CarsPerHour() {
        val estimator = LiveMovementEstimator()
        estimator.observePositions(0L, mapOf("A" to 20))
        for (at in 20_000L until 600_000L step 20_000L) {
            estimator.observePositions(at, mapOf("A" to 20))
        }
        estimator.observePositions(600_000L, mapOf("A" to 19))
        assertEquals(6.0, estimator.estimate(600_000L)!!.positionsPerHour, 0.01)
    }

    @Test
    fun anUnobservedLongGapCannotBeCountedAsFreshMovement() {
        val estimator = LiveMovementEstimator()
        estimator.observePositions(0L, mapOf("A" to 100))
        estimator.observePositions(2 * 3_600_000L, mapOf("A" to 10))
        assertNull(estimator.estimate(2 * 3_600_000L))
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
