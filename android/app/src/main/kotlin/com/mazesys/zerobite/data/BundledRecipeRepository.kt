package com.mazesys.zerobite.data

import android.content.res.AssetManager
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.model.RecipeCatalogue
import com.mazesys.zerobite.core.recipes.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The catalogue bundled from /shared-assets/recipes.json; read once, then cached. */
class BundledRecipeRepository(private val assets: AssetManager) : RecipeRepository {
    private val mutex = Mutex()
    private var cached: List<Recipe>? = null

    override suspend fun recipes(): List<Recipe> = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            RecipeCatalogue.decode(assets.open("recipes.json").bufferedReader().use { it.readText() })
        }.also { cached = it }
    }
}
