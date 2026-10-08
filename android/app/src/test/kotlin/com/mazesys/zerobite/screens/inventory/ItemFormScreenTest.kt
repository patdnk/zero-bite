package com.mazesys.zerobite.screens.inventory

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
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
class ItemFormScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val saved = mutableListOf<FoodItem>()

    private fun show(dark: Boolean = false) {
        val vm = ItemFormViewModel(ItemFormMode.Add, ItemDraft.new(TestClock.today), FakeInventoryRepository(), TestClock.fixed(), SequentialIds(), saved::add)
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { ItemFormScreen(vm, onCancel = {}) } }
    }

    @Test
    fun `fill in a name and add it`() {
        show()
        compose.onNodeWithTag("itemForm.save").assertIsNotEnabled()
        compose.onNodeWithTag("itemForm.name").performTextInput("Oat milk")
        compose.onNodeWithTag("itemForm.save").assertIsEnabled().performClick()
        assertEquals("Oat milk", saved.single().name)
    }

    @Test
    fun `typical shelf life row follows the category`() {
        show()
        compose.onNodeWithText("Meat").performClick() // second chip, always on screen
        compose.onNodeWithText("Use typical shelf life (3 days)").performClick()
        compose.onNodeWithText("10 Sep 2026").assertExists()
    }

    @Test
    fun `form light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/form_light.png")
    }

    @Test
    fun `form dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/form_dark.png")
    }
}
