package com.mazesys.zerobite.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** The current instant and the user's time zone. Injected everywhere so tests can pin "now". */
interface AppClock {
    val timeZone: TimeZone
    fun now(): Instant
    fun today(): LocalDate = now().toLocalDateTime(timeZone).date
}

object SystemAppClock : AppClock {
    override val timeZone: TimeZone get() = TimeZone.currentSystemDefault()
    override fun now(): Instant = Clock.System.now()
}

class FixedClock(private val instant: Instant, override val timeZone: TimeZone) : AppClock {
    override fun now(): Instant = instant
}
