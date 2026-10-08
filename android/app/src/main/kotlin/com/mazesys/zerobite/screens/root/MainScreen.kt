package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.screens.inventory.InventoryTab
import com.mazesys.zerobite.screens.inventory.InventoryViewModel
import com.mazesys.zerobite.screens.recipes.RecipesTab
import com.mazesys.zerobite.screens.recipes.RecipesViewModel
import com.mazesys.zerobite.ui.components.AppTabBar
import com.mazesys.zerobite.ui.components.TabItem
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

private val MAIN_TABS = listOf(
    TabItem(AppTab.FRIDGE, AppTab.FRIDGE.title, Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
    TabItem(AppTab.RECIPES, AppTab.RECIPES.title, Icons.Outlined.SoupKitchen, Icons.Rounded.SoupKitchen),
    TabItem(AppTab.SETTINGS, AppTab.SETTINGS.title, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** Tabs bound to AppCoordinator, plus app-level sheets and alerts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    // Tab roots live in the activity's store so they survive switching tabs.
    val inventoryList = viewModel(key = "inventory") {
        InventoryViewModel(dependencies.inventory, dependencies.settings, dependencies.clock, dependencies.ids, coordinator.inventory, dependencies.notifications)
    }
    val recipesList = viewModel(key = "recipes") {
        RecipesViewModel(dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, coordinator.recipes)
    }
    val tabState = rememberSaveableStateHolder()
    val presentedId = coordinator.presentedSheet?.id
    LaunchedEffect(presentedId) { sheetStores.retainOnly(presentedId) }

    // Android convention: back on another tab returns to the start tab before leaving the app.
    BackHandler(enabled = coordinator.selectedTab != AppTab.FRIDGE) { coordinator.selectTab(AppTab.FRIDGE) }

    Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                tabState.SaveableStateProvider(coordinator.selectedTab) {
                    when (coordinator.selectedTab) {
                        AppTab.FRIDGE -> InventoryTab(coordinator.inventory, inventoryList, dependencies)
                        AppTab.RECIPES -> RecipesTab(coordinator.recipes, recipesList, dependencies)
                        AppTab.SETTINGS -> PlaceholderTab(overline = "Zero Bite", title = "Settings")
                    }
                }
            }
            // Hidden while typing so the bottom search field sits directly on the keyboard.
            if (!WindowInsets.isImeVisible) {
                AppTabBar(MAIN_TABS, coordinator.selectedTab, coordinator::selectTab, Modifier.navigationBarsPadding())
            }
        }
        SheetHost(coordinator, sheetStores, dependencies)
        coordinator.alert?.let { AppAlert(it, coordinator::dismissAlert) }
    }
}
