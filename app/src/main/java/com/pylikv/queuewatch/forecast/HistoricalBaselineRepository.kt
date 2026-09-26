package com.pylikv.queuewatch.forecast

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

class HistoricalBaselineRepository(context: Context) {
    private val root = runCatching {
        JSONObject(context.assets.open("forecast_baselines_v1.json").bufferedReader().use { it.readText() })
    }.getOrNull()

    fun find(checkpointId: String, vehicleType: String, now: Long): HistoricalEstimate? {
        val data = root ?: return null
        val cutoff = runCatching { Instant.parse(data.getString("data_cutoff")).toEpochMilli() }.getOrNull() ?: return null
        if (now - cutoff !in 0..30L * 86_400_000L) return null
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Minsk")).hour / 3 * 3
        val entries = data.optJSONArray("entries") ?: return null
        val candidates = (0 until entries.length()).map { entries.getJSONObject(it) }.filter {
            it.optString("checkpoint_id") == checkpointId && it.optString("vehicle_type") == vehicleType &&
                it.optInt("sample_count") >= if (it.optInt("hour_bucket_start") == -1) 10 else 15
        }
        val row = candidates.firstOrNull { it.optInt("hour_bucket_start") == hour }
            ?: candidates.firstOrNull { it.optInt("hour_bucket_start") == -1 } ?: return null

        val movementCycle = row.optDouble("movement_cycle_minutes", 40.0)
            .takeIf { it.isFinite() && it in 20.0..90.0 } ?: 40.0

        return HistoricalEstimate(
            row.getDouble("positions_per_hour"),
            row.getInt("sample_count"),
            row.optDouble("absolute_error_p50_minutes", 0.0),
            row.optDouble("absolute_error_p80_minutes", 0.0),
            cutoff,
            movementCycle
        )
    }
}
