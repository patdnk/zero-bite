package com.mazesys.zerobite.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AppClockTest {

    @Test
    fun `today is the calendar day in the clock's time zone`() {
        // 23:30 UTC on 6 Sep is 00:30 BST on 7 Sep.
        val clock = FixedClock(Instant.parse("2026-09-06T23:30:00Z"), TimeZone.of("Europe/London"))
        assertEquals(LocalDate(2026, 9, 7), clock.today())
        assertEquals(LocalDate(2026, 9, 6), FixedClock(clock.now(), TimeZone.UTC).today())
    }
}
