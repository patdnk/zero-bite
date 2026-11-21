package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.core.model.ExpiryStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class ThemeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `dark theme swaps the palette`() {
        var light: ZeroBiteColors? = null
        var dark: ZeroBiteColors? = null
        compose.setContent {
            ZeroBiteTheme(darkTheme = false) { light = ZeroBiteTheme.colors }
            ZeroBiteTheme(darkTheme = true) { dark = ZeroBiteTheme.colors }
        }
        assertEquals(LightColors, light)
        assertEquals(DarkColors, dark)
    }

    @Test
    fun `falls back to the system font when Mona Sans was not fetched`() {
        val loaded = FontFamily.Serif
        assertSame(FontFamily.Default, AppFont.resolve(emptyList()) { loaded })
        assertSame(loaded, AppFont.resolve(listOf("mona_sans.ttf")) { loaded })
    }

    @Test
    fun `type scale mirrors iOS Dynamic Type defaults`() {
        val type = ZeroBiteTypography.of(FontFamily.Default)
        assertEquals(34.sp, type.largeTitle.fontSize)
        assertEquals(15.sp, type.subheadline.fontSize)
        assertEquals(FontWeight.SemiBold, type.headline.fontWeight)
        assertEquals(FontWeight.SemiBold, type.caption.semibold().fontWeight)
    }

    @Test
    fun `status tints follow the warning ramp`() {
        assertEquals(LightColors.statusExpired, ExpiryStatus.EXPIRED.tint(LightColors))
        assertEquals(LightColors.statusToday, ExpiryStatus.TODAY.tint(LightColors))
        assertEquals(LightColors.statusSoon, ExpiryStatus.SOON.tint(LightColors))
        assertEquals(DarkColors.statusFresh, ExpiryStatus.FRESH.tint(DarkColors))
    }
}
