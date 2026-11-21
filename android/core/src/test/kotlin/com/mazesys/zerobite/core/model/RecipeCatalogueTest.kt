package com.mazesys.zerobite.core.model

import com.mazesys.zerobite.core.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeCatalogueTest {

    @Test
    fun `bundled catalogue loads and is well formed`() {
        val recipes = RecipeCatalogue.decode(Fixtures.sharedAsset("recipes.json"))
        assertTrue(recipes.size >= 30)
        assertEquals(recipes.size, recipes.map { it.id }.toSet().size)
        for (recipe in recipes) {
            assertTrue(recipe.requiredIngredients.isNotEmpty(), "${recipe.id} has no required ingredients")
            assertTrue(recipe.steps.isNotEmpty(), "${recipe.id} has no steps")
        }
    }

    @Test
    fun `ingredient amount and optional default when absent`() {
        val json = """[{"id":"x","title":"X","summary":"","ingredients":[{"name":"eggs"}],"steps":["Cook"],"prepMinutes":5,"servings":1,"tags":[],"futureField":1}]"""
        val ingredient = RecipeCatalogue.decode(json).single().ingredients.single()
        assertEquals(Recipe.Ingredient(name = "eggs", amount = "", optional = false), ingredient)
    }
}
