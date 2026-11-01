package com.pylikv.queuewatch.forecast

import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastFormattingTest {

    @Test
    fun formatsHoursAndMinutesWithoutFakePrecision() {
        assertEquals(
            "2 ч 20 мин",
            formatEtaMinutes(
                140.0
            )
        )
    }

    @Test
    fun roundsEtaToNearestFiveMinutes() {
        assertEquals(
            "2 ч 20 мин",
            formatEtaMinutes(
                142.0
            )
        )

        assertEquals(
            "2 ч 25 мин",
            formatEtaMinutes(
                143.0
            )
        )
    }

    @Test
    fun formatsMinutesBelowOneHour() {
        assertEquals(
            "35 мин",
            formatEtaMinutes(
                34.9
            )
        )
    }

    @Test
    fun formatsConfidenceInRussian() {
        assertEquals(
            "низкая",
            formatConfidence(
                ForecastConfidence.LOW
            )
        )

        assertEquals(
            "средняя",
            formatConfidence(
                ForecastConfidence.MEDIUM
            )
        )

        assertEquals(
            "высокая",
            formatConfidence(
                ForecastConfidence.HIGH
            )
        )
    }

    @Test
    fun formatsSpeedAsWholePositionsPerHour() {
        assertEquals(
            "21 поз/ч",
            formatForecastSpeed(
                20.8
            )
        )
    }
}
