package com.mazesys.zerobite.screens.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.inventory.ItemFormScreen
import com.mazesys.zerobite.screens.inventory.ItemFormViewModel
import com.mazesys.zerobite.screens.recipes.CookedScreen
import com.mazesys.zerobite.screens.recipes.CookedViewModel
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Full-screen sheets that slide up over the tabs (add/edit item, scan). */
@Composable
fun SheetHost(coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    AnimatedContent(
        targetState = coordinator.presentedSheet,
        transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { it } },
        contentKey = { it?.id },
        label = "sheet",
    ) { sheet ->
        if (sheet == null) return@AnimatedContent
        BackHandler { coordinator.dismissSheet() }
        CompositionLocalProvider(LocalViewModelStoreOwner provides sheetStores.owner(sheet.id)) {
            Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).systemBarsPadding().imePadding()) {
                when (val destination = sheet.destination) {
                    SheetDestination.Scan -> ScanPlaceholder(onClose = coordinator::dismissSheet, onAddManually = coordinator.inventory::showAddItem)
                    is SheetDestination.AddItem -> ItemFormSheet(ItemFormMode.Add, destination.draft, coordinator, dependencies)
                    is SheetDestination.EditItem -> ItemFormSheet(ItemFormMode.Edit(destination.itemId), null, coordinator, dependencies)
                    is SheetDestination.Cooked -> CookedSheet(destination.recipeId, coordinator, dependencies)
                }
            }
        }
    }
}

@Composable
private fun ItemFormSheet(mode: ItemFormMode, draft: ItemDraft?, coordinator: AppCoordinator, dependencies: AppDependencies) {
    val form = viewModel {
        ItemFormViewModel(mode, draft, dependencies.inventory, dependencies.clock, dependencies.ids, onSaved = { coordinator.dismissSheet() })
    }
    ItemFormScreen(form, onCancel = coordinator::dismissSheet)
}

@Composable
private fun CookedSheet(recipeId: String, coordinator: AppCoordinator, dependencies: AppDependencies) {
    val cooked = viewModel {
        CookedViewModel(recipeId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, onDone = { coordinator.dismissSheet() })
    }
    CookedScreen(cooked)
}
