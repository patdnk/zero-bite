package com.mazesys.zerobite.data

import android.content.res.Configuration
import android.os.LocaleList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.assertEquals

/** The recipe catalogue follows the language Android resolves the UI to, not just the first system language. */
@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {

    private fun language(vararg tags: String): String =
        AppLanguage.of(Configuration().apply { setLocales(LocaleList(*tags.map(Locale::forLanguageTag).toTypedArray())) })

    @Test
    fun `a single supported language is used`() {
        assertEquals("pl", language("pl-PL"))
        assertEquals("en", language("en-GB"))
    }

    @Test
    fun `an unsupported language ahead of a supported one is skipped`() {
        assertEquals("pl", language("de-DE", "pl-PL"))
        assertEquals("en", language("de-DE", "en-GB", "pl-PL"))
    }

    @Test
    fun `an unsupported language alone has no polish overlay`() {
        assertEquals("de", language("de-DE")) // BundledRecipeRepository serves English for it
    }
}
