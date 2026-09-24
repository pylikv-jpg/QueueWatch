package com.pylikv.queuewatch.forecast

import kotlin.math.abs
import kotlin.math.max

/** Independent of tracking state and alerts. A forecast never establishes a call. */
object ForecastEngineV1 {
    const val VERSION = "v1.1"
    fun estimate(input: ForecastInput): ForecastResult {
        if (input.currentPosition <= 0) return ForecastResult.Unavailable("invalid_position")
        val history = input.historical?.takeIf {
            validRate(it.positionsPerHour) && it.sampleCount >= 10 &&
                it.absoluteErrorP80Minutes.isFinite() && it.absoluteErrorP80Minutes >= 0 &&
                it.absoluteErrorP50Minutes.isFinite() && it.absoluteErrorP50Minutes >= 0
        }
        val live = input.live?.takeIf {
            validRate(it.positionsPerHour) && it.sampleCount > 0 &&
                input.nowMillis - it.newestSampleAtMillis in 0..3_600_000L
        }
        if (input.currentPosition == 1) return ForecastResult.Available(
            0.0, 0.0, 0.0, ForecastConfidence.LOW, 0.0, VERSION
        ) // At the front, not confirmation of being called.
        if (history == null && live == null) return ForecastResult.Unavailable("insufficient_speed_data")
        val weight = when {
            live == null -> 0.0
            history == null -> 1.0
            live.sampleCount < 3 -> 0.15
            live.sampleCount < 6 -> 0.30
            else -> 0.55
        }
        val speed = when {
            history == null -> live!!.positionsPerHour
            live == null -> history.positionsPerHour
            else -> history.positionsPerHour * (1 - weight) + live.positionsPerHour * weight
        }
        val eta = (input.currentPosition - 1) * 60.0 / speed
        val calibrated = history != null && history.absoluteErrorP80Minutes > 0
        val disagreement = history != null && live != null &&
            abs(history.positionsPerHour - live.positionsPerHour) / history.positionsPerHour > 0.5
        val confidence = when {
            !calibrated || disagreement || (history != null && history.dataCutoffMillis > 0 && input.nowMillis - history.dataCutoffMillis > 7 * 86_400_000L) -> ForecastConfidence.LOW
            history!!.sampleCount >= 50 && live != null && live.sampleCount >= 6 -> ForecastConfidence.HIGH
            history.sampleCount >= 15 -> ForecastConfidence.MEDIUM
            else -> ForecastConfidence.LOW
        }
        // An uncalibrated interval is explicitly marked as provisional by the UI.
        val radius = max(history?.absoluteErrorP80Minutes ?: 0.0,
            if (confidence == ForecastConfidence.LOW) max(30.0, eta * 0.5) else 10.0)
        return ForecastResult.Available(eta, max(0.0, eta - radius), eta + radius,
            confidence, speed, VERSION)
    }

    private fun validRate(rate: Double) = rate.isFinite() && rate in 0.25..200.0
}
