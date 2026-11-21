package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
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
class RecipeDetailScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private var shown: String? = null
    private var cooked: String? = null

    private fun show(id: String = "omelette", dark: Boolean = false) {
        val vm = RecipeDetailViewModel(
            id, FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(),
            onShowItem = { shown = it }, onCooked = { cooked = it },
        )
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { RecipeDetailScreen(vm, onBack = {}) } }
    }

    @Test
    fun `ingredient rows explain where each ingredient comes from`() {
        show()
        compose.onNodeWithText("You have Eggs · Tomorrow").assertExists()
        compose.onNodeWithText("Store cupboard").assertExists()
        compose.onNodeWithText("Optional — skip it").assertExists()
        compose.onNodeWithText("You have everything you need.").assertExists()
        compose.onNodeWithText("You have Eggs · Tomorrow").performClick()
        assertEquals("eggs", shown)
    }

    @Test
    fun `missing ingredients are listed`() {
        show("stirfry")
        compose.onNodeWithText("Missing bell pepper.").assertExists()
        compose.onNodeWithText("You don't have this").assertExists()
    }

    @Test
    fun `cooked button opens the sheet`() {
        show()
        compose.onNodeWithTag("recipe.cooked").performScrollTo().assertIsEnabled().performClick()
        assertEquals("omelette", cooked)
    }

    @Test
    fun `unknown recipe shows not found`() {
        show("nope")
        compose.onNodeWithText("Recipe not found").assertExists()
    }

    @Test
    fun `recipe detail light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_detail_light.png")
    }

    @Test
    fun `recipe detail dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_detail_dark.png")
    }
}
