package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.theme.LightColors
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecipeViewsTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 9, 7)

    private fun recipe(title: String, vararg ingredients: String) = Recipe(
        id = title.lowercase(), title = title, summary = "", prepMinutes = 10, servings = 2, tags = emptyList(), steps = listOf("Cook"),
        ingredients = ingredients.map { Recipe.Ingredient(it) },
    )

    private fun item(name: String, days: Int) = FoodItem(
        id = name, name = name, category = FoodCategory.DAIRY,
        addedAt = Instant.parse("2026-09-01T09:00:00Z"), expiresOn = today.plus(days, DateTimeUnit.DAY),
    )

    private fun match(recipe: Recipe, matched: Int, missing: List<String> = emptyList(), expiring: List<FoodItem> = emptyList()) = RecipeMatch(
        recipe = recipe,
        matched = recipe.ingredients.take(matched).map { MatchedIngredient(it, item(it.name, 10)) },
        missing = missing.map { Recipe.Ingredient(it) },
        missingOptional = emptyList(),
        expiringItems = expiring,
        score = 0.0,
    )

    @Test
    fun `artwork icon comes from the title`() {
        assertEquals(Icons.Rounded.Egg, recipe("Classic cheese omelette").icon)
        assertEquals(Icons.Rounded.SoupKitchen, recipe("Tomato soup").icon)
        assertEquals(Icons.Rounded.Restaurant, recipe("Mystery dish").icon)
    }

    @Test
    fun `status text and match percent read like iOS`() {
        val omelette = recipe("Omelette", "eggs", "butter", "milk")
        assertEquals("Ready to cook", match(omelette, 3).statusText)
        assertEquals(100, match(omelette, 3).matchPercent)
        assertEquals("Missing milk", match(omelette, 2, listOf("milk")).statusText)
        assertEquals(67, match(omelette, 2, listOf("milk")).matchPercent)
        assertEquals("Missing a, b +2", match(omelette, 0, listOf("a", "b", "c", "d")).statusText)
    }

    @Test
    fun `accent prefers expiring food, then ready, then missing`() {
        val omelette = recipe("Omelette", "eggs")
        assertEquals(LightColors.brandOrange, match(omelette, 1, expiring = listOf(item("eggs", 1))).accent(LightColors))
        assertEquals(LightColors.statusFresh, match(omelette, 1).accent(LightColors))
        assertEquals(LightColors.brandYellow, match(omelette, 0, listOf("eggs")).accent(LightColors))
    }

    @Test
    fun `cards show percent, expiring tags and react to taps`() {
        var opened = 0
        val omelette = recipe("Omelette", "eggs", "butter", "milk")
        compose.setContent {
            ZeroBiteTheme {
                Column {
                    FeaturedRecipeCard(match(omelette, 2, listOf("milk"), expiring = listOf(item("Eggs", 1))), onClick = { opened++ })
                    RecipeRow(match(omelette, 3))
                }
            }
        }
        compose.onNodeWithContentDescription("67 percent of ingredients in your fridge").assertExists()
        compose.onNodeWithText("Eggs").assertExists()
        compose.onNodeWithText("10 min · Ready to cook").assertExists()
        compose.onAllNodesWithText("Omelette")[0].performClick() // featured card (the row shows the title too)
        assertEquals(1, opened)
    }

    @Test
    fun `expiring chip opens its item`() {
        var tapped = 0
        compose.setContent { ZeroBiteTheme { ExpiringChip(item("Spinach", 0), today, 3, onClick = { tapped++ }) } }
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Spinach").performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun `recipe views light`() = gallery(false)

    @Test
    fun `recipe views dark`() = gallery(true)

    private fun gallery(dark: Boolean) {
        val omelette = recipe("Classic cheese omelette", "eggs", "butter", "milk")
        val soup = recipe("Tomato soup", "tomatoes", "onion", "stock", "cream")
        compose.setContent {
            ZeroBiteTheme(darkTheme = dark) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeaturedRecipeCard(match(omelette, 3, expiring = listOf(item("Eggs", 1), item("Butter", 2))), onClick = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecipeCard(match(soup, 3, listOf("cream")), onClick = {}, modifier = Modifier.weight(1f))
                        RecipeCard(match(omelette, 3), onClick = {}, modifier = Modifier.weight(1f))
                    }
                    RecipeRow(match(soup, 2, listOf("cream", "onion")))
                    ExpiringChip(item("Spinach", 0), today, 3, onClick = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/recipe_views_${if (dark) "dark" else "light"}.png")
    }
}
