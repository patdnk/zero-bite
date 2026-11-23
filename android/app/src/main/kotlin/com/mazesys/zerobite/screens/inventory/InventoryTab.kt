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
    fun send(event: InventoryCoordinator.Event) {
        coordinator.eventLoop(event)
    }
    NavDisplay(
        backStack = coordinator.state.backStack,
        modifier = modifier,
        onBack = { send(InventoryCoordinator.Event.DidTapBack) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<InventoryRoute.Fridge> { InventoryScreen(listViewModel) }
            entry<InventoryRoute.ItemDetail> { route ->
                ItemDetailEntry(
                    route.itemId, dependencies,
                    onEdit = { send(InventoryCoordinator.Event.DidTapEditItem(it)) },
                    onShowRecipe = { send(InventoryCoordinator.Event.DidSelectRecipe(it)) },
                    onDeleted = { send(InventoryCoordinator.Event.DidDeleteItem) },
                    onBack = { send(InventoryCoordinator.Event.DidTapBack) },
                )
            }
            entry<InventoryRoute.RecipeDetail> { route ->
                RecipeDetailEntry(
                    route.recipeId, dependencies,
                    onShowItem = { send(InventoryCoordinator.Event.DidSelectItem(it)) },
                    onCooked = { send(InventoryCoordinator.Event.DidTapCooked(it)) },
                    onBack = { send(InventoryCoordinator.Event.DidTapBack) },
                )
            }
        },
    )
}
