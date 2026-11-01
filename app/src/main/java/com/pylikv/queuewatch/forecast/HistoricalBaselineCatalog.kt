package com.pylikv.queuewatch.forecast

import com.pylikv.queuewatch.VehicleType

data class HistoricalBaselineEntry(
    val checkpointId: String,
    val vehicleType: VehicleType,
    val hourBucketStart: Int,
    val hourBucketSize: Int,
    val estimate: HistoricalEstimate
)

class HistoricalBaselineCatalog(
    private val entries:
        List<HistoricalBaselineEntry>
) {

    fun find(
        checkpointId: String,
        vehicleType: VehicleType,
        localHour: Int
    ): HistoricalEstimate? {
        if (
            localHour !in
                0..23
        ) {
            return null
        }

        val exact =
            entries.firstOrNull {
                it.checkpointId ==
                    checkpointId &&
                    it.vehicleType ==
                        vehicleType &&
                    it.hourBucketStart >=
                        0 &&
                    it.hourBucketSize >
                        0 &&
                    localHour >=
                        it.hourBucketStart &&
                    localHour <
                        it.hourBucketStart +
                            it.hourBucketSize
            }

        if (
            exact != null
        ) {
            return exact.estimate
        }

        return entries
            .firstOrNull {
                it.checkpointId ==
                    checkpointId &&
                    it.vehicleType ==
                        vehicleType &&
                    it.hourBucketStart ==
                        -1
            }
            ?.estimate
    }
}
