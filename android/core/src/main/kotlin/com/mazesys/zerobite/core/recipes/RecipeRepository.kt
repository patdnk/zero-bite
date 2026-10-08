package com.mazesys.zerobite.core.recipes

import com.mazesys.zerobite.core.model.Recipe

/** Source of recipes: the bundled catalogue today, a shared recipe database later. */
interface RecipeRepository {
    suspend fun recipes(): List<Recipe>
}
