package com.pylikv.queuewatch.forecast

enum class ForecastConfidence {
    LOW,
    MEDIUM,
    HIGH
}

data class SpeedEstimate(
    val positionsPerHour: Double,
    val sampleCount: Int,
    val newestSampleAtMillis: Long
)

data class HistoricalEstimate(
    val positionsPerHour: Double,
    val sampleCount: Int,
    val absoluteErrorP50Minutes: Double,
    val absoluteErrorP80Minutes: Double,
    val dataCutoffMillis: Long = 0L
)

data class ForecastInput(
    val currentPosition: Int,
    val queueCount: Int,
    val historical: HistoricalEstimate?,
    val live: SpeedEstimate?,
    val nowMillis: Long
)

sealed interface ForecastResult {
    data class Available(
        val etaMinutes: Double,
        val lowMinutes: Double,
        val highMinutes: Double,
        val confidence: ForecastConfidence,
        val effectivePositionsPerHour: Double,
        val algorithmVersion: String = "v1"
    ) : ForecastResult

    data class Unavailable(
        val reason: String
    ) : ForecastResult
}
