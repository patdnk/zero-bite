package com.mazesys.zerobite.data

import android.content.res.AssetManager
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.model.RecipeCatalogue
import com.mazesys.zerobite.core.recipes.IngredientAliases
import com.mazesys.zerobite.core.recipes.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The catalogue bundled from /shared-assets: recipes.json, shown through recipes.pl.json and the
 * Polish tag names when the app language is Polish, plus the Polish ingredient aliases (always on).
 * Each file is read once, then cached.
 */
class BundledRecipeRepository(
    private val assets: AssetManager,
    private val language: StateFlow<String>,
) : RecipeRepository {
    private val mutex = Mutex()
    private var english: List<Recipe>? = null
    private var tagNames: Map<String, Map<String, String>>? = null
    private val localized = mutableMapOf<String, List<Recipe>>()
    private var aliases: IngredientAliases? = null

    override suspend fun recipes(): List<Recipe> = recipes(language.value)

    override val catalogue: Flow<List<Recipe>> get() = language.map { recipes(it) }

    override suspend fun ingredientAliases(): IngredientAliases = mutex.withLock {
        aliases ?: withContext(Dispatchers.IO) {
            IngredientAliases.decode(read("ingredient-aliases.pl.json"))
        }.also { aliases = it }
    }

    /** The catalogue in [language] ("en", "pl"); English for languages without a translation. */
    suspend fun recipes(language: String): List<Recipe> = mutex.withLock {
        localized[language] ?: withContext(Dispatchers.IO) {
            val base = english ?: RecipeCatalogue.decode(read("recipes.json")).also { english = it }
            val tags = tagNames ?: RecipeCatalogue.decodeTagNames(read("recipe-tags.json")).also { tagNames = it }
            val translations = if (language in TRANSLATED) RecipeCatalogue.decodeTranslations(read("recipes.$language.json")) else emptyMap()
            RecipeCatalogue.localize(base, translations, tags[language] ?: tags[ENGLISH].orEmpty())
        }.also { localized[language] = it }
    }

    private fun read(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    private companion object {
        const val ENGLISH = "en"

        /** Languages with a bundled recipes.<language>.json overlay. */
        val TRANSLATED = setOf("pl")
    }
}
