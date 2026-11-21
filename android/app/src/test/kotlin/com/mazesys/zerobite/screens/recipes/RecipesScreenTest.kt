package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testRecipes
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.Dispatchers
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
class RecipesScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    private val app = AppCoordinator { TestClock.today }

    private fun show(dark: Boolean = false) {
        val vm = RecipesViewModel(FakeInventoryRepository(recipeFridge()), FakeSettings(), FakeRecipes(testRecipes()), TestClock.fixed(), app.recipes, Dispatchers.Unconfined)
        compose.setContent { ZeroBiteTheme(darkTheme = dark) { RecipesScreen(vm) } }
    }

    @Test
    fun `shows the strip, scopes and the featured recipe`() {
        show()
        compose.onNode(hasText("Recipes") and isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Use these first").assertIsDisplayed()
        compose.onNodeWithText("You can make these now").assertIsDisplayed()
        compose.onNodeWithText("Omelette").performClick()
        assertEquals(RecipesRoute.RecipeDetail("omelette"), app.recipes.backStack.last())
    }

    @Test
    fun `nearly scope and search`() {
        show()
        compose.onNodeWithText("Nearly").performClick()
        compose.onNodeWithText("Missing one or two things").assertIsDisplayed()
        compose.onNodeWithTag("recipes.search").performTextInput("salad")
        compose.onNodeWithText("No near-misses").assertIsDisplayed()
    }

    @Test
    fun `recipes light`() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/recipes_light.png")
    }

    @Test
    fun `recipes dark`() {
        show(dark = true)
        compose.onRoot().captureRoboImage("src/test/screenshots/recipes_dark.png")
    }
}
