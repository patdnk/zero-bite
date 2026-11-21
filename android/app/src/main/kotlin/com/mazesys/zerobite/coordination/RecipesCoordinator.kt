package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface RecipesRoute : NavKey {
    @Serializable
    data object Catalogue : RecipesRoute

    @Serializable
    data class RecipeDetail(val recipeId: String) : RecipesRoute

    @Serializable
    data class ItemDetail(val itemId: String) : RecipesRoute
}

/** Navigation within the Recipes tab. */
class RecipesCoordinator(private val parent: AppCoordinator) {

    val backStack: SnapshotStateList<RecipesRoute> = mutableStateListOf(RecipesRoute.Catalogue)

    fun showRecipe(id: String) {
        backStack.add(RecipesRoute.RecipeDetail(id))
    }

    fun showItem(id: String) {
        backStack.add(RecipesRoute.ItemDetail(id))
    }

    fun showEditItem(id: String) = parent.presentSheet(SheetDestination.EditItem(id))

    fun showCooked(recipeId: String) = parent.presentSheet(SheetDestination.Cooked(recipeId))

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
