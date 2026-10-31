package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Source of recipes: the bundled catalogue today, a shared recipe database later. */
interface RecipeRepository {
    /** The catalogue in the app's current language. */
    suspend fun recipes(): List<Recipe>

    /** The catalogue, emitted again in the new language whenever the app language changes. */
    val catalogue: Flow<List<Recipe>> get() = flow { emit(recipes()) }

    /** Other-language item names that match ingredients; active whatever the app language. */
    suspend fun ingredientAliases(): IngredientAliases = IngredientAliases.NONE
}
