package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoricalBaselineCatalogTest {

    private val entries =
        listOf(
            HistoricalBaselineEntry(
                checkpointId = "checkpoint-a",
                vehicleType = VehicleType.TRUCK,
                hourBucketStart = 12,
                hourBucketSize = 3,
                estimate =
                    HistoricalEstimate(
                        positionsPerHour = 18.4,
                        sampleCount = 72,
                        absoluteErrorP50Minutes = 24.0,
                        absoluteErrorP80Minutes = 52.0
                    )
            ),
            HistoricalBaselineEntry(
                checkpointId = "checkpoint-a",
                vehicleType = VehicleType.TRUCK,
                hourBucketStart = -1,
                hourBucketSize = 24,
                estimate =
                    HistoricalEstimate(
                        positionsPerHour = 14.0,
                        sampleCount = 120,
                        absoluteErrorP50Minutes = 35.0,
                        absoluteErrorP80Minutes = 70.0
                    )
            )
        )

    @Test
    fun exactThreeHourBucketWinsOverGlobalFallback() {
        val catalog =
            HistoricalBaselineCatalog(
                entries
            )

        val estimate =
            catalog.find(
                checkpointId =
                    "checkpoint-a",
                vehicleType =
                    VehicleType.TRUCK,
                localHour =
                    13
            )

        assertEquals(
            18.4,
            estimate!!.positionsPerHour,
            0.001
        )
        assertEquals(
            72,
            estimate.sampleCount
        )
    }

    @Test
    fun missingHourBucketFallsBackToCheckpointAndTypeGlobal() {
        val catalog =
            HistoricalBaselineCatalog(
                entries
            )

        val estimate =
            catalog.find(
                checkpointId =
                    "checkpoint-a",
                vehicleType =
                    VehicleType.TRUCK,
                localHour =
                    19
            )

        assertEquals(
            14.0,
            estimate!!.positionsPerHour,
            0.001
        )
        assertEquals(
            120,
            estimate.sampleCount
        )
    }

    @Test
    fun differentVehicleTypeDoesNotReuseTruckBaseline() {
        val catalog =
            HistoricalBaselineCatalog(
                entries
            )

        assertNull(
            catalog.find(
                checkpointId =
                    "checkpoint-a",
                vehicleType =
                    VehicleType.CAR,
                localHour =
                    13
            )
        )
    }

    @Test
    fun invalidHourDoesNotMatchBucket() {
        val catalog =
            HistoricalBaselineCatalog(
                entries
            )

        assertNull(
            catalog.find(
                checkpointId =
                    "checkpoint-b",
                vehicleType =
                    VehicleType.TRUCK,
                localHour =
                    25
            )
        )
    }
}
