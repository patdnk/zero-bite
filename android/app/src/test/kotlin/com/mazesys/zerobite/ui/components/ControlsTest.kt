package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ControlsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `chip filter selects an option`() {
        compose.setContent {
            var selected by remember { mutableStateOf(StorageLocation.FRIDGE) }
            ZeroBiteTheme { ChipFilter(StorageLocation.entries, selected, { selected = it }, { it.label }) }
        }
        compose.onNodeWithText("Cupboard").performClick()
        compose.onNodeWithText("Cupboard").assertIsSelected()
    }

    @Test
    fun `search bar types, clears and runs its accessory`() {
        var scans = 0
        var latest = ""
        compose.setContent {
            var text by remember { mutableStateOf("") }
            ZeroBiteTheme {
                BottomSearchBar(
                    text = text,
                    onTextChange = { text = it; latest = it },
                    placeholder = "Search your food",
                    accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan label or barcode") { scans++ },
                )
            }
        }
        compose.onNodeWithText("Search your food").assertExists()
        compose.onNodeWithContentDescription("Scan label or barcode").performClick()
        assertEquals(1, scans)
        compose.onNodeWithTag("search.field").performTextInput("milk")
        assertEquals("milk", latest)
        compose.onNodeWithText("Cancel").assertExists() // focused: Cancel replaces the accessory
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", latest)
    }

    @Test
    fun `tab bar selects tabs`() {
        val tabs = listOf(
            TabItem("fridge", "Fridge", Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
            TabItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
        )
        compose.setContent {
            var selected by remember { mutableStateOf("fridge") }
            ZeroBiteTheme { AppTabBar(tabs, selected, { selected = it }) }
        }
        compose.onNodeWithTag("tab.settings").performClick()
        compose.onNodeWithTag("tab.settings").assertIsSelected()
    }

    @Test
    fun `top bar back calls back`() {
        var backs = 0
        compose.setContent { ZeroBiteTheme { TopBar(TopBarLeading.Back { backs++ }) } }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `labeled input shows its placeholder until typed into`() {
        compose.setContent {
            var name by remember { mutableStateOf("") }
            ZeroBiteTheme { LabeledInput("Name", name, { name = it }, placeholder = "Greek yogurt", testTag = "name") }
        }
        compose.onNodeWithText("Greek yogurt").assertExists()
        compose.onNodeWithTag("name").performTextInput("Milk")
        compose.onNodeWithText("Greek yogurt").assertDoesNotExist()
    }

    @Test
    fun `empty state actions fire`() {
        var primary = false
        var secondary = false
        compose.setContent {
            ZeroBiteTheme {
                EmptyStateView(
                    title = "Your fridge is empty",
                    message = "Scan a label or add something by hand.",
                    icon = Icons.Rounded.Kitchen,
                    primaryTitle = "Scan a label", onPrimary = { primary = true },
                    secondaryTitle = "Try it with sample food", onSecondary = { secondary = true },
                )
            }
        }
        compose.onNodeWithText("Scan a label").performClick()
        compose.onNodeWithText("Try it with sample food").performClick()
        assertTrue(primary && secondary)
    }

    @Test
    fun `controls gallery light`() = gallery(dark = false)

    @Test
    fun `controls gallery dark`() = gallery(dark = true)

    private fun gallery(dark: Boolean) {
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TopBar(TopBarLeading.Close {})
                    ChipFilter(StorageLocation.entries, StorageLocation.FRIDGE, {}, { it.label })
                    CardSection(
                        title = "Details",
                        footer = "No date on the label? Use the typical shelf life.",
                        rows = listOf<@Composable () -> Unit>(
                            { InfoRow("Stored in", "Fridge") },
                            { InfoRow("Quantity", "500 g") },
                            { ActionRow("Use typical shelf life (7 days)", onClick = {}) },
                        ),
                    )
                    LabeledInput("Name", "", {}, placeholder = "Greek yogurt")
                    BottomSearchBar("", {}, "Search your food", accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan") {})
                    AppTabBar(
                        listOf(
                            TabItem("fridge", "Fridge", Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
                            TabItem("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
                        ),
                        "fridge", {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/controls_${if (dark) "dark" else "light"}.png")
    }
}
