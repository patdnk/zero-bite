package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.runBlocking
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
class ItemDetailScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val milk = inventory.current.first { it.name == "Semi-skimmed milk" }
    private var deleted = 0

    private fun show(dark: Boolean = false) {
        val vm = ItemDetailViewModel(milk.id, inventory, FakeSettings(), FakeRecipes(), TestClock.fixed(), onEdit = {}, onShowRecipe = {}, onDeleted = { deleted++ })
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { ItemDetailScreen(vm, onBack = {}) } }
    }

    @Test
    fun `shows status and details`() {
        show()
        compose.onNodeWithText("Semi-skimmed milk").assertIsDisplayed()
        compose.onNodeWithText("Expires in 2 days").assertIsDisplayed()
        compose.onNodeWithText("9 September 2026").assertExists()
        compose.onNodeWithText("2 pints").assertExists()
    }

    @Test
    fun `mark as used up then put it back`() {
        show()
        compose.onNodeWithText("Mark as used up").performClick()
        compose.onNodeWithText("Used up").assertIsDisplayed()
        compose.onNodeWithText("Put it back in the fridge").performClick()
        compose.onNodeWithText("Mark as used up").assertExists()
    }

    @Test
    fun `delete asks for confirmation`() {
        show()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete this item?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `item removed elsewhere shows the removed state`() {
        show()
        runBlocking { inventory.delete(milk.id) }
        compose.onNodeWithText("Item removed").assertIsDisplayed()
    }

    @Test
    fun `detail light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/detail_light.png")
    }

    @Test
    fun `detail dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/detail_dark.png")
    }
}
