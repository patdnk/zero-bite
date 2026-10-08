package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
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
class InventoryScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val app = AppCoordinator { TestClock.today }

    private fun show(inventory: FakeInventoryRepository, dark: Boolean = false, fontScale: Float? = null) {
        val vm = InventoryViewModel(inventory, FakeSettings(), TestClock.fixed(), SequentialIds(), app.inventory)
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                if (fontScale == null) {
                    InventoryScreen(vm)
                } else {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { InventoryScreen(vm) }
                }
            }
        }
    }

    @Test
    fun `empty fridge offers sample food`() {
        show(FakeInventoryRepository())
        compose.onNodeWithText("Your fridge is empty").assertIsDisplayed()
        compose.onNodeWithText("Try it with sample food").performClick()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens its detail`() {
        val inventory = FakeInventoryRepository(sampleFridge())
        show(inventory)
        compose.onNodeWithText("Spinach").performClick()
        val spinach = inventory.current.first { it.name == "Spinach" }
        assertEquals(InventoryRoute.ItemDetail(spinach.id), app.inventory.backStack.last())
    }

    @Test
    fun `search narrows the grid`() {
        show(FakeInventoryRepository(sampleFridge()))
        compose.onNodeWithTag("inventory.search").performTextInput("milk")
        compose.onNodeWithText("Semi-skimmed milk").assertIsDisplayed()
        compose.onNodeWithText("Spinach").assertDoesNotExist()
    }

    @Test
    fun `large font scale keeps names visible`() {
        show(FakeInventoryRepository(sampleFridge()), fontScale = 2f)
        compose.onNode(hasText("Fridge") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_font2.png")
    }

    @Test
    fun `fridge light`() {
        show(FakeInventoryRepository(sampleFridge()))
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_light.png")
    }

    @Test
    fun `fridge dark`() {
        show(FakeInventoryRepository(sampleFridge()), dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_dark.png")
    }

    @Test
    fun `empty fridge light`() {
        show(FakeInventoryRepository())
        compose.onRoot().captureRoboImage("src/test/screenshots/fridge_empty_light.png")
    }
}
