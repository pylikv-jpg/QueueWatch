package com.pylikv.queuewatch.forecast

import android.content.Context
import com.pylikv.queuewatch.VehicleType
import org.json.JSONObject

class HistoricalBaselineRepository(
    context: Context
) {

    companion object {
        private const val ASSET_NAME =
            "forecast_baselines_v1.json"
    }

    private val catalog =
        loadCatalog(
            context.applicationContext
        )

    fun find(
        checkpointId: String,
        vehicleType: VehicleType,
        localHour: Int
    ): HistoricalEstimate? {

        return catalog.find(
            checkpointId =
                checkpointId,
            vehicleType =
                vehicleType,
            localHour =
                localHour
        )
    }

    private fun loadCatalog(
        context: Context
    ): HistoricalBaselineCatalog {

        return try {
            val text =
                context.assets
                    .open(
                        ASSET_NAME
                    )
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val root =
                JSONObject(
                    text
                )

            val entriesJson =
                root.optJSONArray(
                    "entries"
                )

            val entries =
                mutableListOf<
                    HistoricalBaselineEntry
                >()

            if (
                entriesJson != null
            ) {
                for (
                    index
                    in 0 until
                        entriesJson.length()
                ) {
                    val item =
                        entriesJson
                            .optJSONObject(
                                index
                            )
                            ?: continue

                    val entry =
                        parseEntry(
                            item
                        )
                            ?: continue

                    entries.add(
                        entry
                    )
                }
            }

            HistoricalBaselineCatalog(
                entries
            )

        } catch (
            _: Exception
        ) {
            HistoricalBaselineCatalog(
                emptyList()
            )
        }
    }

    private fun parseEntry(
        item: JSONObject
    ): HistoricalBaselineEntry? {

        return try {
            val checkpointId =
                item.getString(
                    "checkpoint_id"
                )

            val vehicleType =
                VehicleType.valueOf(
                    item.getString(
                        "vehicle_type"
                    )
                )

            val hourBucketStart =
                item.getInt(
                    "hour_bucket_start"
                )

            val hourBucketSize =
                item.getInt(
                    "hour_bucket_size"
                )

            val positionsPerHour =
                item.getDouble(
                    "positions_per_hour"
                )

            val sampleCount =
                item.getInt(
                    "sample_count"
                )

            val p50 =
                item.getDouble(
                    "absolute_error_p50_minutes"
                )

            val p80 =
                item.getDouble(
                    "absolute_error_p80_minutes"
                )

            if (
                checkpointId.isBlank() ||
                positionsPerHour <= 0.0 ||
                positionsPerHour >
                    200.0 ||
                sampleCount <=
                    0 ||
                p50 <
                    0.0 ||
                p80 <
                    p50
            ) {
                return null
            }

            HistoricalBaselineEntry(
                checkpointId =
                    checkpointId,
                vehicleType =
                    vehicleType,
                hourBucketStart =
                    hourBucketStart,
                hourBucketSize =
                    hourBucketSize,
                estimate =
                    HistoricalEstimate(
                        positionsPerHour =
                            positionsPerHour,
                        sampleCount =
                            sampleCount,
                        absoluteErrorP50Minutes =
                            p50,
                        absoluteErrorP80Minutes =
                            p80
                    )
            )

        } catch (
            _: Exception
        ) {
            null
        }
    }
}
