package com.mazesys.zerobite.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A recipe from the bundled catalogue (shared-assets/recipes.json). */
@Serializable
data class Recipe(
    val id: String,
    val title: String,
    val summary: String,
    val ingredients: List<Ingredient>,
    val steps: List<String>,
    val prepMinutes: Int,
    val servings: Int,
    val tags: List<String>,
) {
    @Serializable
    data class Ingredient(
        val name: String,
        val amount: String = "",
        val optional: Boolean = false,
    )

    val requiredIngredients: List<Ingredient> get() = ingredients.filterNot { it.optional }
}

object RecipeCatalogue {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): List<Recipe> = json.decodeFromString(text)
}
