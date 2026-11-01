package com.mazesys.zerobite.screens.recipes

import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.IngredientAliases
import com.mazesys.zerobite.testing.TestClock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Test
import kotlin.test.assertEquals

class RecipesPolishMatchingTest {

    private val recipe = Recipe(
        id = "omlet", title = "Omlet", summary = "", steps = listOf("Usmaż"), prepMinutes = 5, servings = 1, tags = emptyList(),
        ingredients = listOf(Recipe.Ingredient("eggs", amount = "3", label = "jajka", missingLabel = "jajek")),
    )
    private val aliases = IngredientAliases(mapOf("egg" to listOf("jajka")))
    private val eggs = FoodItem(id = "e", name = "Jajka", addedAt = TestClock.now, expiresOn = TestClock.today.plus(5, DateTimeUnit.DAY))

    private fun build(query: String = "", aliases: IngredientAliases = this.aliases, scope: RecipeScope = RecipeScope.ALL) =
        RecipesUiState.build(MatchedRecipes.of(listOf(eggs), listOf(recipe), aliases, 3, TestClock.today), scope, query)

    @Test
    fun `aliases make a polish item satisfy an english ingredient`() {
        assertEquals(listOf("omlet"), build(scope = RecipeScope.READY).displayed.map { it.id })
        assertEquals(emptyList(), build(aliases = IngredientAliases.NONE, scope = RecipeScope.READY).displayed)
    }

    @Test
    fun `search finds the ingredient by its polish label and english name`() {
        assertEquals(listOf("omlet"), build("jajka").displayed.map { it.id })
        assertEquals(listOf("omlet"), build("eggs").displayed.map { it.id })
        assertEquals(emptyList(), build("mleko").displayed)
    }

    @Test
    fun `search finds an ingredient through its aliases`() {
        val pancake = recipe.copy(ingredients = listOf(Recipe.Ingredient("milk", label = "mleka", missingLabel = "mleka")))
        val milkAliases = IngredientAliases(mapOf("milk" to listOf("mleko", "mleka")))
        val found = RecipesUiState.build(MatchedRecipes.of(listOf(eggs), listOf(pancake), milkAliases, 3, TestClock.today), RecipeScope.ALL, "mleko")
        assertEquals(listOf("omlet"), found.displayed.map { it.id })
        val none = RecipesUiState.build(MatchedRecipes.of(listOf(eggs), listOf(pancake), IngredientAliases.NONE, 3, TestClock.today), RecipeScope.ALL, "mleko")
        assertEquals(emptyList(), none.displayed)
    }
}
