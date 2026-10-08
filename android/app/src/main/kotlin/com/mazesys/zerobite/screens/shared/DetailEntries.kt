package com.mazesys.zerobite.screens.shared

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.screens.inventory.ItemDetailScreen
import com.mazesys.zerobite.screens.inventory.ItemDetailViewModel
import com.mazesys.zerobite.screens.recipes.RecipeDetailScreen
import com.mazesys.zerobite.screens.recipes.RecipeDetailViewModel

/** Item detail as a navigation entry; used by both the Fridge and Recipes tabs. */
@Composable
fun ItemDetailEntry(
    itemId: String,
    dependencies: AppDependencies,
    onEdit: (String) -> Unit,
    onShowRecipe: (String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val detail = viewModel(key = "item-$itemId") {
        ItemDetailViewModel(
            itemId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock,
            onEdit = onEdit, onShowRecipe = onShowRecipe, onDeleted = onDeleted,
        )
    }
    ItemDetailScreen(detail, onBack = onBack)
}

/** Recipe detail as a navigation entry; used by both tabs. */
@Composable
fun RecipeDetailEntry(
    recipeId: String,
    dependencies: AppDependencies,
    onShowItem: (String) -> Unit,
    onCooked: (String) -> Unit,
    onBack: () -> Unit,
) {
    val detail = viewModel(key = "recipe-$recipeId") {
        RecipeDetailViewModel(
            recipeId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock,
            onShowItem = onShowItem, onCooked = onCooked,
        )
    }
    RecipeDetailScreen(detail, onBack = onBack)
}
