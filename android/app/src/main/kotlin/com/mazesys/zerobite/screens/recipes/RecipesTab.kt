package com.mazesys.zerobite.screens.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.RecipesCoordinator
import com.mazesys.zerobite.coordination.RecipesRoute
import com.mazesys.zerobite.screens.shared.ItemDetailEntry
import com.mazesys.zerobite.screens.shared.RecipeDetailEntry

/** The Recipes tab: catalogue → recipe → item. */
@Composable
fun RecipesTab(
    coordinator: RecipesCoordinator,
    listViewModel: RecipesViewModel,
    dependencies: AppDependencies,
    modifier: Modifier = Modifier,
) {
    fun send(event: RecipesCoordinator.Event) {
        coordinator.eventLoop(event)
    }
    NavDisplay(
        backStack = coordinator.state.backStack,
        modifier = modifier,
        onBack = { send(RecipesCoordinator.Event.DidTapBack) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<RecipesRoute.Catalogue> { RecipesScreen(listViewModel) }
            entry<RecipesRoute.RecipeDetail> { route ->
                RecipeDetailEntry(
                    route.recipeId, dependencies,
                    onShowItem = { send(RecipesCoordinator.Event.DidSelectItem(it)) },
                    onCooked = { send(RecipesCoordinator.Event.DidTapCooked(it)) },
                    onBack = { send(RecipesCoordinator.Event.DidTapBack) },
                )
            }
            entry<RecipesRoute.ItemDetail> { route ->
                ItemDetailEntry(
                    route.itemId, dependencies,
                    onEdit = { send(RecipesCoordinator.Event.DidTapEditItem(it)) },
                    onShowRecipe = { send(RecipesCoordinator.Event.DidSelectRecipe(it)) },
                    onDeleted = { send(RecipesCoordinator.Event.DidDeleteItem) },
                    onBack = { send(RecipesCoordinator.Event.DidTapBack) },
                )
            }
        },
    )
}
