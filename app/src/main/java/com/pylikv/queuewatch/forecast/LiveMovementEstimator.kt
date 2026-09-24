package com.pylikv.queuewatch.forecast

/** Counts elapsed stationary time too; a batch is one advance, regardless of fleet size. */
class LiveMovementEstimator {
    private data class Interval(val start: Long, val end: Long, val advance: Double)
    private var previous: Map<String, Int> = emptyMap()
    private var previousAt: Long? = null
    private val intervals = ArrayDeque<Interval>()
    var dataGap = false
        private set

    fun observePositions(at: Long, positions: Map<String, Int>) {
        val before = previousAt
        if (before != null && at <= before) return
        val current = positions.filterValues { it > 0 }
        if (before != null) {
            val elapsed = at - before
            // Missing API observations are unknown time, never assumed stationary.
            if (elapsed > 10 * 60_000L || previous.isEmpty() || current.isEmpty()) {
                intervals.clear()
                dataGap = true
            } else {
                val deltas = current.mapNotNull { (id, position) -> previous[id]?.let { it - position } }
                val positive = deltas.filter { it > 0 }.sorted()
                val movement = if (positive.isEmpty()) 0.0 else {
                    val mid = positive.size / 2
                    if (positive.size % 2 == 1) positive[mid].toDouble()
                    else (positive[mid - 1].toDouble() + positive[mid]) / 2
                }
                // Ignore isolated re-ordering when most matching vehicles did not move.
                val supported = positive.isEmpty() || deltas.size < 3 || positive.size * 2 >= deltas.size
                if (deltas.isNotEmpty() && deltas.none { it < 0 } && supported && movement <= 100) {
                    intervals.addLast(Interval(before, at, movement))
                } else {
                    intervals.clear()
                    dataGap = true
                }
            }
        }
        previous = current
        previousAt = at
        trim(at)
        if (intervals.sumOf { it.end - it.start } >= 30 * 60_000L) dataGap = false
    }

    fun estimate(now: Long): SpeedEstimate? {
        trim(now)
        if (previousAt == null || now - previousAt!! !in 0..120_000L) return null
        val covered = intervals.sumOf { it.end - it.start }
        // Do not extrapolate a minute-long burst into an hourly rate.
        if (covered < 10 * 60_000L) return null
        val batches = intervals.count { it.advance > 0 }
        if (batches == 0) return null
        val speed = intervals.sumOf { it.advance } * 3_600_000.0 / covered
        if (speed !in 0.25..200.0) return null
        val newest = intervals.lastOrNull { it.advance > 0 }!!.end
        return SpeedEstimate(speed, batches, newest)
    }

    fun stationary(now: Long): Boolean = previousAt != null && now - previousAt!! in 0..120_000L &&
        intervals.filter { it.start >= now - 30 * 60_000L }.let { recent ->
            recent.sumOf { it.end - it.start } >= 20 * 60_000L && recent.all { it.advance == 0.0 }
        }

    private fun trim(now: Long) {
        while (intervals.isNotEmpty() && intervals.first().start < now - 3_600_000L) intervals.removeFirst()
    }
}
