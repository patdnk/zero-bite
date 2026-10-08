package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.DateCandidate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Finds best-before / use-by dates in OCR'd label text. UK/EU day-month-year ordering is
 * assumed for ambiguous numerics. Port of the iOS ExpiryDateParser — keep them in step via
 * testdata/expiry-dates.json.
 */
class ExpiryDateParser(private val today: LocalDate) {

    private enum class Kind { YMD, DMY, D_MON_Y, MON_D_Y, D_MON, MON_Y, M_Y, COMPACT8, COMPACT6 }

    private class Pattern(val kind: Kind, val regex: Regex, val baseConfidence: Double)

    fun parse(text: String): List<DateCandidate> = parse(text.lines())

    /** Candidates sorted by confidence (desc), then date (asc), one per day. */
    fun parse(lines: List<String>): List<DateCandidate> {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val found = mutableListOf<DateCandidate>()

        cleaned.forEachIndexed { index, line ->
            val hasKeyword = containsKeyword(line)
            val previousHasKeyword = index > 0 && containsKeyword(cleaned[index - 1])
            val looksLikeManufacture = MANUFACTURE.containsMatchIn(line)
            val consumed = mutableListOf<IntRange>()

            for (pattern in PATTERNS) {
                // Bare digit runs are only worth considering when the line says "use by" etc.
                if ((pattern.kind == Kind.COMPACT6 || pattern.kind == Kind.COMPACT8) && !hasKeyword) continue

                for (match in pattern.regex.findAll(line)) {
                    if (consumed.any { it.overlaps(match.range) }) continue
                    consumed += match.range
                    val date = makeDate(pattern.kind, match) ?: continue

                    var confidence = pattern.baseConfidence
                    if (hasKeyword) confidence += 0.2 else if (previousHasKeyword) confidence += 0.15
                    if (looksLikeManufacture) confidence -= 0.3

                    val days = today.daysUntil(date)
                    if (days < -365) confidence -= 0.3 else if (days < -30) confidence -= 0.1
                    if (days > 365 * 5) confidence -= 0.15

                    found += DateCandidate(date, match.value, line, confidence.coerceIn(0.0, 1.0))
                }
            }
        }

        val bestPerDay = LinkedHashMap<LocalDate, DateCandidate>()
        for (candidate in found) {
            val existing = bestPerDay[candidate.date]
            if (existing != null && existing.confidence >= candidate.confidence) continue
            bestPerDay[candidate.date] = candidate
        }
        return bestPerDay.values.sortedWith(
            compareByDescending<DateCandidate> { it.confidence }.thenBy { it.date }
        )
    }

    fun bestDate(lines: List<String>): LocalDate? = parse(lines).firstOrNull()?.date

    private fun makeDate(kind: Kind, match: MatchResult): LocalDate? {
        fun group(i: Int): String = match.groups[i]?.value.orEmpty()
        fun int(i: Int): Int? = group(i).toIntOrNull()
        fun monthName(i: Int): Int? = MONTH_NAMES.indexOf(group(i).lowercase().take(3)).takeIf { it >= 0 }?.plus(1)
        fun year(i: Int): Int? = int(i)?.let { if (it < 100) 2000 + it else it }

        var day: Int? = null
        var month: Int? = null
        var yearValue: Int? = null
        var endOfMonth = false
        var yearInferred = false

        when (kind) {
            Kind.YMD -> { yearValue = year(1); month = int(2); day = int(3) }
            Kind.DMY -> {
                day = int(1); month = int(2); yearValue = year(3)
                // "09/14/2026" can only be month-first; "12/09/2026" is read day-first (UK).
                val d = day
                val m = month
                if (d != null && m != null && d <= 12 && m > 12) { day = m; month = d }
            }
            Kind.D_MON_Y -> { day = int(1); month = monthName(2); yearValue = year(3) }
            Kind.MON_D_Y -> { month = monthName(1); day = int(2); yearValue = year(3) }
            Kind.D_MON -> { day = int(1); month = monthName(2); yearInferred = true }
            Kind.MON_Y -> { month = monthName(1); yearValue = year(2); endOfMonth = true }
            Kind.M_Y -> { month = int(1); yearValue = year(2); endOfMonth = true }
            Kind.COMPACT8, Kind.COMPACT6 -> { day = int(1); month = int(2); yearValue = year(3) }
        }

        val m = month?.takeIf { it in 1..12 } ?: return null
        if (yearInferred) yearValue = today.year
        val y = yearValue?.takeIf { it in (today.year - 2)..(today.year + 15) } ?: return null

        var date = if (endOfMonth) {
            LocalDate(y, m, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        } else {
            val d = day?.takeIf { it in 1..31 } ?: return null
            // Rejects impossible dates such as 31 Feb.
            runCatching { LocalDate(y, m, d) }.getOrNull() ?: return null
        }

        if (yearInferred && date < today.minus(60, DateTimeUnit.DAY)) {
            date = date.plus(1, DateTimeUnit.YEAR)
        }
        return date
    }

    companion object {
        private val KEYWORD = Regex(
            """\b(best before end|best before|best by|bbe|bb|use by|use before|expiry|expires|expire|exp|display until|sell by|consume by|eat by)(?![a-z])""",
            RegexOption.IGNORE_CASE,
        )
        private val MANUFACTURE = Regex(
            """\b(packed on|packed|manufactured|mfg|mfd|prod\.?|production|batch|lot)(?![a-z])""",
            RegexOption.IGNORE_CASE,
        )
        private val MONTH_NAMES = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        private const val MONTH = """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?"""
        private const val NOT_DIGIT_BEFORE = """(?<!\d)"""
        private const val NOT_DIGIT_AFTER = """(?!\d)"""

        private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

        private val PATTERNS = listOf(
            Pattern(Kind.YMD, rx(NOT_DIGIT_BEFORE + """(\d{4})[./-](\d{1,2})[./-](\d{1,2})""" + NOT_DIGIT_AFTER), 0.8),
            Pattern(Kind.D_MON_Y, rx(NOT_DIGIT_BEFORE + """(\d{1,2})(?:st|nd|rd|th)?\s*[-./]?\s*""" + MONTH + """\s*[-./,']?\s*(\d{2,4})""" + NOT_DIGIT_AFTER), 0.85),
            Pattern(Kind.MON_D_Y, rx("""\b""" + MONTH + """\s*(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{2,4})""" + NOT_DIGIT_AFTER), 0.8),
            Pattern(Kind.DMY, rx(NOT_DIGIT_BEFORE + """(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})""" + NOT_DIGIT_AFTER), 0.75),
            Pattern(Kind.MON_Y, rx("""\b""" + MONTH + """\s*[-./,']?\s*(\d{4})""" + NOT_DIGIT_AFTER), 0.6),
            Pattern(Kind.D_MON, rx(NOT_DIGIT_BEFORE + """(\d{1,2})(?:st|nd|rd|th)?\s*[-./]?\s*""" + MONTH + """\b"""), 0.5),
            Pattern(Kind.M_Y, rx("""(?<![\d./-])(\d{1,2})[./-](\d{4})""" + NOT_DIGIT_AFTER), 0.55),
            Pattern(Kind.COMPACT8, rx(NOT_DIGIT_BEFORE + """(\d{2})(\d{2})(\d{4})""" + NOT_DIGIT_AFTER), 0.3),
            Pattern(Kind.COMPACT6, rx(NOT_DIGIT_BEFORE + """(\d{2})(\d{2})(\d{2})""" + NOT_DIGIT_AFTER), 0.25),
        )

        /** True when the line is about the expiry date ("use by", "best before", "exp"…). */
        fun containsKeyword(line: String): Boolean = KEYWORD.containsMatchIn(line)

        private fun IntRange.overlaps(other: IntRange) = first <= other.last && other.first <= last
    }
}
