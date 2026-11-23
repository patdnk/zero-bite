package com.mazesys.zerobite.screens.root

import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.BuildConfig
import com.mazesys.zerobite.screens.settings.SettingsAlertHost
import com.mazesys.zerobite.screens.settings.SettingsScreen
import com.mazesys.zerobite.screens.settings.SettingsViewModel
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
import com.mazesys.zerobite.coordination.MainCoordinator
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.screens.inventory.InventoryTab
import com.mazesys.zerobite.screens.inventory.InventoryViewModel
import com.mazesys.zerobite.screens.recipes.RecipesTab
import com.mazesys.zerobite.screens.recipes.RecipesViewModel
import com.mazesys.zerobite.ui.components.AppTabBar
import com.mazesys.zerobite.ui.components.TabItem
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

@Composable
private fun mainTabs() = listOf(
    TabItem(AppTab.FRIDGE, stringResource(AppTab.FRIDGE.title), Icons.Outlined.Kitchen, Icons.Rounded.Kitchen),
    TabItem(AppTab.RECIPES, stringResource(AppTab.RECIPES.title), Icons.Outlined.SoupKitchen, Icons.Rounded.SoupKitchen),
    TabItem(AppTab.SETTINGS, stringResource(AppTab.SETTINGS.title), Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/** Tabs bound to MainCoordinator, plus the sheet on top of them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(coordinator: MainCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    // Tab roots live in the activity's store so they survive switching tabs.
    val inventoryList = viewModel(key = "inventory") {
        InventoryViewModel(dependencies.inventory, dependencies.settings, dependencies.clock, dependencies.ids, coordinator.inventory, dependencies.notifications, dependencies.strings)
    }
    val recipesList = viewModel(key = "recipes") {
        RecipesViewModel(dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, coordinator.recipes, dependencies.computation)
    }
    val settingsScreen = viewModel(key = "settings") {
        SettingsViewModel(dependencies, coordinator.settings, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
    val tabState = rememberSaveableStateHolder()
    val state = coordinator.state
    val presentedId = state.sheet?.id
    LaunchedEffect(presentedId) { sheetStores.retainOnly(presentedId) }
    val openTab: (AppTab) -> Unit = { coordinator.eventLoop(MainCoordinator.Event.DidSelectTab(it)) }

    // Android convention: back on another tab returns to the start tab before leaving the app.
    BackHandler(enabled = state.selectedTab != AppTab.FRIDGE) { openTab(AppTab.FRIDGE) }

    Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                tabState.SaveableStateProvider(state.selectedTab) {
                    when (state.selectedTab) {
                        AppTab.FRIDGE -> InventoryTab(coordinator.inventory, inventoryList, dependencies)
                        AppTab.RECIPES -> RecipesTab(coordinator.recipes, recipesList, dependencies)
                        AppTab.SETTINGS -> SettingsScreen(settingsScreen)
                    }
                }
            }
            // Hidden while typing so the bottom search field sits directly on the keyboard.
            if (!WindowInsets.isImeVisible) {
                AppTabBar(mainTabs(), state.selectedTab, openTab, Modifier.navigationBarsPadding())
            }
        }
        SheetHost(coordinator, sheetStores, dependencies)
    }
    SettingsAlertHost(settingsScreen)
}
