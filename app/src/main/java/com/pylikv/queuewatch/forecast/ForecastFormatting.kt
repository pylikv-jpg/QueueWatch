package com.pylikv.queuewatch.forecast

import kotlin.math.roundToInt

fun formatEtaMinutes(
    value: Double
): String {
    if (
        !value.isFinite() ||
        value < 0.0
    ) {
        return "—"
    }

    val roundedMinutes =
        (
            value /
                5.0
            )
            .roundToInt()
            .coerceAtLeast(
                0
            ) *
            5

    val hours =
        roundedMinutes /
            60

    val minutes =
        roundedMinutes %
            60

    return when {
        hours > 0 &&
            minutes > 0 ->
            "${hours} ч ${minutes} мин"

        hours > 0 ->
            "${hours} ч"

        else ->
            "${minutes} мин"
    }
}

fun formatConfidence(
    confidence:
        ForecastConfidence
): String =
    when (
        confidence
    ) {
        ForecastConfidence.LOW ->
            "низкая"

        ForecastConfidence.MEDIUM ->
            "средняя"

        ForecastConfidence.HIGH ->
            "высокая"
    }

fun formatForecastSpeed(
    positionsPerHour: Double
): String {
    if (
        !positionsPerHour.isFinite() ||
        positionsPerHour <
            0.0
    ) {
        return "—"
    }

    return "${positionsPerHour.roundToInt()} поз/ч"
}


/** Calendar interval is anchored to observation time, never to a screen reopening. */
fun formatCallWindow(at: Long?, lowMinutes: Double?, highMinutes: Double?, now: Long): String {
    if (at == null || lowMinutes == null || highMinutes == null ||
        now - at !in 0L..120_000L || !lowMinutes.isFinite() || !highMinutes.isFinite() ||
        lowMinutes < 0 || highMinutes < lowMinutes || highMinutes > 365 * 24 * 60) {
        return "Недостаточно свежих данных для расчёта времени вызова."
    }
    val low = java.util.Date(at + (lowMinutes * 60_000).toLong())
    val high = java.util.Date(at + (highMinutes * 60_000).toLong())
    val date = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("ru")).apply {
        timeZone = java.util.TimeZone.getTimeZone("Europe/Minsk")
    }
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale("ru")).apply {
        timeZone = java.util.TimeZone.getTimeZone("Europe/Minsk")
    }
    return if (date.format(low) == date.format(high)) {
        "${date.format(low)}, с ${clock.format(low)} до ${clock.format(high)}"
    } else "С ${date.format(low)}, ${clock.format(low)} до ${date.format(high)}, ${clock.format(high)}"
}
