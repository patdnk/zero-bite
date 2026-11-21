package com.mazesys.zerobite.ui.format

import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class DateTextTest {
    private val date = LocalDate(2026, 9, 7)
    private val polish = Locale.forLanguageTag("pl-PL")

    @Test
    fun `english keeps the en-GB strings`() {
        for (locale in listOf(Locale.UK, Locale.US, Locale.ENGLISH)) {
            assertEquals("Mon 7 Sep", DateText.header(date, locale))
            assertEquals("7 September 2026", DateText.long(date, locale))
            assertEquals("7 Sep 2026", DateText.abbreviated(date, locale))
            assertEquals("7 Sep", DateText.dayMonth(date, locale))
        }
    }

    @Test
    fun `polish uses genitive month names`() {
        assertEquals("pon. 7 wrz", DateText.header(date, polish))
        assertEquals("7 września 2026", DateText.long(date, polish))
        assertEquals("7 wrz 2026", DateText.abbreviated(date, polish))
        assertEquals("12 paź", DateText.dayMonth(LocalDate(2026, 10, 12), polish))
    }
}
