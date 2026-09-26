package com.pylikv.queuewatch.forecast

import kotlin.math.max
import kotlin.math.min

/**
 * Robust speed estimate for a single tracked vehicle.
 *
 * Queue movement is bursty: several vehicles can move in a minute and then wait
 * for a long time. The estimator therefore uses elapsed wall-clock time, not only
 * movement intervals. A historical/virtual minimum window prevents the first
 * burst from being extrapolated to an unrealistic hourly rate.
 */
class LiveMovementEstimator {
    private data class Sample(val at: Long, val position: Int)

    private val samples = ArrayDeque<Sample>()
    private var previousAt: Long? = null

    var dataGap = false
        private set

    fun observePosition(at: Long, position: Int) {
        if (position <= 0) return
        val before = previousAt
        if (before != null && at <= before) return

        if (before != null && at - before > 10 * MINUTE) {
            samples.clear()
            dataGap = true
        }

        samples.addLast(Sample(at, position))
        previousAt = at
        trim(at)

        if (samples.size >= 2 && samples.last().at - samples.first().at >= 30 * MINUTE) {
            dataGap = false
        }
    }

    /**
     * [virtualWindowMinutes] is the initial time denominator. Example: if ten
     * places are gained during the first few minutes and the expected movement
     * cycle is 40 minutes, the initial rate is 10 / 40 min = 15 positions/hour,
     * not 10 / 5 min = 120 positions/hour.
     *
     * After enough real time has elapsed, the real rolling observation window
     * replaces the virtual one. The long window is capped at three hours.
     * A 60-minute recent rate may slow the estimate quickly, while acceleration
     * is admitted only gradually.
     */
    fun estimate(
        now: Long,
        virtualWindowMinutes: Double = 40.0,
        minimumObservedMinutes: Double = 30.0
    ): SpeedEstimate? {
        trim(now)
        if (samples.size < 2) return null

        val latest = samples.last()
        if (now - latest.at !in 0..120_000L) return null

        val oldest = samples.first()
        val observedMillis = latest.at - oldest.at
        if (observedMillis < minimumObservedMinutes * MINUTE) return null

        val advance = (oldest.position - latest.position).coerceAtLeast(0)
        if (advance == 0) return null

        val virtualMillis = (virtualWindowMinutes.coerceIn(20.0, 90.0) * MINUTE).toLong()
        val denominator = max(observedMillis, virtualMillis)
        val longRate = advance * HOUR.toDouble() / denominator
        if (!validRate(longRate)) return null

        val recentCutoff = latest.at - HOUR
        var recentBase = oldest
        for (sample in samples) {
            if (sample.at <= recentCutoff) recentBase = sample else break
        }
        val recentCovered = latest.at - recentBase.at
        val recentAdvance = (recentBase.position - latest.position).coerceAtLeast(0)

        var rate = longRate
        if (recentCovered >= 30 * MINUTE) {
            val recentRate = recentAdvance * HOUR.toDouble() / recentCovered.coerceAtLeast(1L)
            if (recentRate.isFinite()) {
                rate = if (recentRate <= longRate) {
                    // Slowdowns should affect ETA quickly.
                    longRate * 0.60 + recentRate * 0.40
                } else {
                    // A burst must persist before it can materially increase speed.
                    min(longRate * 1.20, longRate * 0.85 + recentRate * 0.15)
                }
            }
        }

        if (!validRate(rate)) return null
        val observedMinutes = (observedMillis / MINUTE).coerceAtLeast(1L).toInt()
        return SpeedEstimate(rate, observedMinutes, latest.at)
    }

    fun stationary(now: Long): Boolean {
        trim(now)
        if (samples.size < 2) return false
        val latest = samples.last()
        if (now - latest.at !in 0..120_000L) return false

        val cutoff = latest.at - 30 * MINUTE
        var base = samples.first()
        for (sample in samples) {
            if (sample.at <= cutoff) base = sample else break
        }
        val covered = latest.at - base.at
        val advance = base.position - latest.position
        return covered >= 20 * MINUTE && advance <= 0
    }

    private fun trim(now: Long) {
        val cutoff = now - 3 * HOUR
        // Keep one sample before the cutoff so the three-hour slope remains continuous.
        while (samples.size > 2 && samples.elementAt(1).at < cutoff) {
            samples.removeFirst()
        }
    }

    private fun validRate(rate: Double) = rate.isFinite() && rate in 0.25..200.0

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
