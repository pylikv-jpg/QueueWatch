package com.pylikv.queuewatch.forecast

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ForecastCallWindowTest {
    private val at = Instant.parse("2026-11-06T08:00:00Z").toEpochMilli()
    @Test fun displaysBothBoundsInMinskTime() {
        assertEquals("6 ноября 2026, с 11:30 до 12:30", formatCallWindow(at, 30.0, 90.0, at))
    }
    @Test fun reopeningScreenDoesNotMoveTheCallWindow() {
        assertEquals(formatCallWindow(at, 30.0, 90.0, at), formatCallWindow(at, 30.0, 90.0, at + 60_000))
    }
    @Test fun includesBothDatesWhenWindowCrossesMidnight() {
        val late = Instant.parse("2026-11-06T20:30:00Z").toEpochMilli()
        assertEquals("С 6 ноября 2026, 23:45 до 7 ноября 2026, 00:30", formatCallWindow(late, 15.0, 60.0, late))
    }
    @Test fun includesCorrectYearsWhenWindowCrossesNewYear() {
        val late = Instant.parse("2026-12-31T20:30:00Z").toEpochMilli()
        assertEquals("С 31 декабря 2026, 23:45 до 1 января 2027, 00:30", formatCallWindow(late, 15.0, 60.0, late))
    }
    @Test fun staleOrAbsentObservationsDoNotShowACurrentWindow() {
        for (value in listOf(formatCallWindow(at,30.0,90.0,at+120_001),formatCallWindow(null,30.0,90.0,at),formatCallWindow(at,null,90.0,at))) {
            assertEquals("Недостаточно свежих данных для расчёта времени вызова.",value)
        }
    }
    @Test fun invalidBoundsAndFutureObservationAreUnavailable() {
        for (value in listOf(formatCallWindow(at,Double.NaN,90.0,at),formatCallWindow(at,90.0,30.0,at),formatCallWindow(at,-1.0,90.0,at),formatCallWindow(at,30.0,Double.POSITIVE_INFINITY,at),formatCallWindow(at,30.0,90.0,at-1))) {
            assertEquals("Недостаточно свежих данных для расчёта времени вызова.",value)
        }
    }
}
