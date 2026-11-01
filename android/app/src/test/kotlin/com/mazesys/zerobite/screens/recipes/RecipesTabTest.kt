package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testRecipes
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipesTabTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `recipe, then its ingredient's item, then back, then cooked`() {
        val dependencies = testDependencies(inventory = FakeInventoryRepository(recipeFridge()), recipes = FakeRecipes(testRecipes()))
        val app = AppCoordinator { TestClock.today }
        val list = RecipesViewModel(dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, app.recipes, dependencies.computation)
        compose.setContent { ZeroBiteTheme { RecipesTab(app.recipes, list, dependencies) } }

        compose.onNodeWithText("Omelette").performClick()
        compose.onNodeWithText("Ingredients").assertIsDisplayed()
        compose.onNodeWithText("You have Eggs · Tomorrow").performClick()
        compose.onNodeWithText("Details").assertIsDisplayed()
        assertEquals(RecipesRoute.ItemDetail("eggs"), app.recipes.backStack.last())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("recipe.cooked").performScrollTo().performClick()
        assertEquals(SheetDestination.Cooked("omelette"), app.presentedSheet?.destination)
    }
}
