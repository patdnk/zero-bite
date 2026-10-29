package com.mazesys.zerobite.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates in the app language, from java.time's locale data. English keeps the en-GB day-month
 * order with the classic abbreviations ("Sep", not CLDR's en-GB "Sept"), matching the iOS app.
 */
object DateText {

    /** "Mon 7 Sep" / "pon. 7 wrz": the Fridge header overline. */
    fun header(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date, "EEE d MMM", locale)

    /** "7 September 2026" / "7 września 2026". */
    fun long(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date, "d MMMM yyyy", locale)

    /** "7 Sep 2026" / "7 wrz 2026". */
    fun abbreviated(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date, "d MMM yyyy", locale)

    /** "12 Sep" / "12 wrz": compact expiry badges. */
    fun dayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String = format(date, "d MMM", locale)

    private fun format(date: LocalDate, pattern: String, locale: Locale): String {
        val formatLocale = if (locale.language == "en") Locale.ENGLISH else locale
        return DateTimeFormatter.ofPattern(pattern, formatLocale).format(date.toJavaLocalDate())
    }
}

/** The app's current language, for formatting inside composables. */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
