package com.mazesys.zerobite.ui.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** en-GB date strings matching the iOS app. */
object DateText {
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_LONG = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** "Mon 7 Sep": the Fridge header overline. */
    fun header(date: LocalDate): String = "${DAYS[date.dayOfWeek.ordinal]} ${date.day} ${MONTHS_SHORT[date.month.number - 1]}"

    /** "7 September 2026". */
    fun long(date: LocalDate): String = "${date.day} ${MONTHS_LONG[date.month.number - 1]} ${date.year}"

    /** "7 Sep 2026". */
    fun abbreviated(date: LocalDate): String = "${date.day} ${MONTHS_SHORT[date.month.number - 1]} ${date.year}"
}
