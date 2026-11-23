package com.mazesys.zerobite.screens.shared

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.inventory.ItemFormScreen
import com.mazesys.zerobite.screens.inventory.ItemFormViewModel
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

/**
 * The add/edit item form wired to its view model; [onDone] runs after a save, [onCancel] on cancel (by default the same).
 * Used by the item sheet and the add-food flow.
 */
@Composable
fun ItemFormEntry(mode: ItemFormMode, draft: ItemDraft?, dependencies: AppDependencies, onDone: () -> Unit, onCancel: () -> Unit = onDone) {
    val form = viewModel {
        ItemFormViewModel(mode, draft, dependencies.inventory, dependencies.clock, dependencies.ids, onSaved = { onDone() })
    }
    ItemFormScreen(form, onCancel = onCancel)
}
