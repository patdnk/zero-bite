package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.tint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PrimitivesTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `buttons call back only when enabled`() {
        var taps = 0
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    AppButton("Used it", onClick = { taps++ })
                    AppButton("Disabled", onClick = { taps += 100 }, enabled = false)
                }
            }
        }
        compose.onNodeWithText("Used it").performClick()
        compose.onNodeWithText("Disabled").assertIsNotEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `loading button hides its title and ignores taps`() {
        var taps = 0
        compose.setContent { ZeroBiteTheme { AppButton("Saving", onClick = { taps++ }, loading = true) } }
        compose.onNodeWithText("Saving").assertDoesNotExist()
        assertEquals(0, taps)
    }

    @Test
    fun `screen header shows the count tag only when given`() {
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    ScreenHeader(title = "Fridge", overline = "Mon 7 Sep", count = 14)
                    ScreenHeader(title = "Recipes")
                }
            }
        }
        compose.onNodeWithContentDescription("14 items").assertExists()
        compose.onNodeWithText("Mon 7 Sep").assertExists()
    }

    @Test
    fun `header icon button is labelled and clickable`() {
        var tapped = false
        compose.setContent { ZeroBiteTheme { HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = { tapped = true }) } }
        compose.onNodeWithContentDescription("Add food").performClick()
        assertEquals(true, tapped)
    }

    @Test
    fun `primitives gallery light`() = gallery(dark = false)

    @Test
    fun `primitives gallery dark`() = gallery(dark = true)

    private fun gallery(dark: Boolean) {
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScreenHeader(title = "Fridge", overline = "Mon 7 Sep", count = 14) {
                        HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = {})
                    }
                    SectionHeading(title = "Expired", detail = "1 item", tint = ExpiryStatus.EXPIRED.tint(ZeroBiteTheme.colors), icon = ExpiryStatus.EXPIRED.icon)
                    AppCard(accent = ZeroBiteTheme.colors.statusSoon) {
                        IconTile(FoodCategory.DAIRY.icon, tint = ZeroBiteTheme.colors.statusSoon)
                        DetailTitle(title = "Greek yogurt", overline = "Dairy & eggs")
                        ShelfLifeBar(progress = 0.7f, tint = ZeroBiteTheme.colors.statusSoon)
                    }
                    Tag("Spinach", tint = ZeroBiteTheme.colors.brandOrange)
                    AppButton("Mark as used up", onClick = {})
                    AppButton("Scan a label", onClick = {}, kind = ButtonKind.BRAND)
                    AppButton("Edit", onClick = {}, kind = ButtonKind.TONAL)
                    AppButton("Not now", onClick = {}, kind = ButtonKind.PLAIN, compact = true)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_${if (dark) "dark" else "light"}.png")
    }
}
