package com.mazesys.zerobite.screens.inventory

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute

/**
 * The Fridge tab: list → item detail. The list's view model is owned by the caller so it
 * survives switching tabs; each detail entry gets its own view model, cleared when popped.
 */
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
                val detail = viewModel(key = route.itemId) {
                    ItemDetailViewModel(
                        itemId = route.itemId,
                        inventory = dependencies.inventory,
                        settings = dependencies.settings,
                        clock = dependencies.clock,
                        onEdit = coordinator::showEditItem,
                        onDeleted = coordinator::pop,
                    )
                }
                ItemDetailScreen(detail, onBack = coordinator::pop)
            }
        },
    )
}
