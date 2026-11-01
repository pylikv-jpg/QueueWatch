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
