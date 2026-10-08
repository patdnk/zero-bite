package com.mazesys.zerobite.core.alerts

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** A date from a notice at day or month precision ("16 July 2026", "May 2027"). */
data class AlertDateSpec(val year: Int, val month: Int, val day: Int?) {
    fun matches(date: LocalDate): Boolean =
        date.year == year && date.month.number == month && (day == null || date.day == day)
}

/**
 * Pulls dates out of FSA batch text such as "10, 16 and 17 July 2026", "end of 28 April 2026",
 * "31 January 2027 02 May 2027" or "May 2027".
 */
object AlertDateParser {

    private const val MONTH =
        "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"

    private val WRITTEN = Regex(
        """((?:\b\d{1,2}(?:st|nd|rd|th)?\b[\s,&]*(?:and\s+)?)*)\b""" + MONTH + """\.?,?\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    private val NUMERIC = Regex("""\b(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})\b""")

    private val NON_DIGITS = Regex("""\D+""")

    private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun dates(text: String): List<AlertDateSpec> {
        val specs = mutableListOf<AlertDateSpec>()

        for (match in WRITTEN.findAll(text)) {
            val year = match.groupValues[3].toIntOrNull() ?: continue
            val month = monthIndex(match.groupValues[2].lowercase()) ?: continue
            val days = match.groupValues[1].split(NON_DIGITS).mapNotNull { it.toIntOrNull() }.filter { it in 1..31 }
            if (days.isEmpty()) {
                specs += AlertDateSpec(year, month, null)
            } else {
                days.mapTo(specs) { AlertDateSpec(year, month, it) }
            }
        }

        for (match in NUMERIC.findAll(text)) {
            val day = match.groupValues[1].toIntOrNull() ?: continue
            val month = match.groupValues[2].toIntOrNull() ?: continue
            var year = match.groupValues[3].toIntOrNull() ?: continue
            if (day !in 1..31 || month !in 1..12) continue
            if (year < 100) year += 2000
            specs += AlertDateSpec(year, month, day)
        }
        return specs
    }

    private fun monthIndex(text: String): Int? =
        MONTH_NAMES.indexOfFirst { text.startsWith(it) }.takeIf { it >= 0 }?.plus(1)
}
