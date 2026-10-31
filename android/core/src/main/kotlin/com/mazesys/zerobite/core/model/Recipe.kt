package com.mazesys.zerobite.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/**
 * A recipe from the bundled catalogue (shared-assets/recipes.json), possibly shown in another
 * language through [RecipeCatalogue.localize]. Ingredient `name`s and `tags` stay the English
 * keys used for matching; the display fields carry the language the user reads.
 */
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
    /** The English title, whatever language [title] is in (picks the placeholder artwork). */
    @Transient val englishTitle: String = title,
    /** [tags] as shown in the app language. */
    @Transient val tagLabels: List<String> = tags,
) {
    @Serializable
    data class Ingredient(
        /** The English canonical name, used for matching. */
        val name: String,
        val amount: String = "",
        val optional: Boolean = false,
        /** Shown after [amount] in the ingredient list ("3 jajka"). */
        @Transient val label: String = name,
        /** Shown in lists of missing ingredients ("Brakuje: jajek"). */
        @Transient val missingLabel: String = name,
    )

    val requiredIngredients: List<Ingredient> get() = ingredients.filterNot { it.optional }
}

/** One recipe in another language (an entry of shared-assets/recipes.<lang>.json). */
@Serializable
data class RecipeTranslation(
    val title: String,
    val summary: String,
    val steps: List<String>,
    /** Keyed by the English ingredient name. */
    val ingredients: Map<String, IngredientTranslation>,
) {
    @Serializable
    data class IngredientTranslation(val name: String, val amount: String = "", val missing: String)
}

object RecipeCatalogue {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): List<Recipe> = json.decodeFromString(text)

    /** shared-assets/recipes.<lang>.json: translations keyed by recipe id. */
    fun decodeTranslations(text: String): Map<String, RecipeTranslation> = json.decodeFromString(text)

    /** shared-assets/recipe-tags.json: tag display names keyed by language, then tag. */
    fun decodeTagNames(text: String): Map<String, Map<String, String>> = json.decodeFromString(text)

    /**
     * Shows [recipes] in another language. Anything without a translation (a recipe, an
     * ingredient, a tag, or steps whose count disagrees) keeps its English text.
     */
    fun localize(recipes: List<Recipe>, translations: Map<String, RecipeTranslation>, tagNames: Map<String, String>): List<Recipe> =
        recipes.map { recipe ->
            val t = translations[recipe.id]
            recipe.copy(
                title = t?.title ?: recipe.title,
                summary = t?.summary ?: recipe.summary,
                steps = t?.steps?.takeIf { it.size == recipe.steps.size } ?: recipe.steps,
                ingredients = recipe.ingredients.map { ingredient ->
                    val i = t?.ingredients?.get(ingredient.name) ?: return@map ingredient
                    ingredient.copy(amount = i.amount, label = i.name, missingLabel = i.missing)
                },
                tagLabels = recipe.tags.map { tagNames[it] ?: it },
            )
        }
}
