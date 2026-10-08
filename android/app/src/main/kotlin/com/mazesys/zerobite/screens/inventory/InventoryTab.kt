package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.screens.shared.ItemDetailEntry
import com.mazesys.zerobite.screens.shared.RecipeDetailEntry

/** The Fridge tab: list → item detail → recipe. The list's view model is owned by the caller. */
@Composable
fun InventoryTab(
    coordinator: InventoryCoordinator,
    listViewModel: InventoryViewModel,
    dependencies: AppDependencies,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = coordinator.backStack,
        modifier = modifier,
        onBack = { coordinator.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<InventoryRoute.Fridge> { InventoryScreen(listViewModel) }
            entry<InventoryRoute.ItemDetail> { route ->
                ItemDetailEntry(
                    route.itemId, dependencies,
                    onEdit = coordinator::showEditItem, onShowRecipe = coordinator::showRecipe,
                    onDeleted = coordinator::pop, onBack = coordinator::pop,
                )
            }
            entry<InventoryRoute.RecipeDetail> { route ->
                RecipeDetailEntry(
                    route.recipeId, dependencies,
                    onShowItem = coordinator::showItem, onCooked = coordinator::showCooked, onBack = coordinator::pop,
                )
            }
        },
    )
}
