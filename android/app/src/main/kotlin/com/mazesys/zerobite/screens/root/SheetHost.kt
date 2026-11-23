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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.MainCoordinator
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.recipes.CookedScreen
import com.mazesys.zerobite.screens.recipes.CookedViewModel
import com.mazesys.zerobite.screens.scan.LiveScanViewModel
import com.mazesys.zerobite.screens.scan.LiveScannerScreen
import com.mazesys.zerobite.screens.scan.ScanSheet
import com.mazesys.zerobite.screens.shared.ItemFormEntry
import com.mazesys.zerobite.screens.welcome.WelcomeMode
import com.mazesys.zerobite.screens.welcome.WelcomeScreen
import com.mazesys.zerobite.screens.welcome.WelcomeViewModel
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Full-screen sheets that slide up over the tabs (add/edit item, scan). */
@Composable
fun SheetHost(coordinator: MainCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    val dismiss: () -> Unit = { coordinator.eventLoop(MainCoordinator.Event.DidDismissSheet) }
    AnimatedContent(
        targetState = coordinator.state.sheet,
        transitionSpec = { slideInVertically { it } togetherWith slideOutVertically { it } },
        contentKey = { it?.id },
        label = "sheet",
    ) { sheet ->
        if (sheet == null) return@AnimatedContent
        BackHandler(onBack = dismiss)
        CompositionLocalProvider(LocalViewModelStoreOwner provides sheetStores.owner(sheet.id)) {
            Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).systemBarsPadding().imePadding()) {
                when (val destination = sheet.destination) {
                    SheetDestination.Scan -> {
                        // Held for this presentation, so a sheet animating out keeps the scan it was showing.
                        val scan = remember { coordinator.scan }
                        if (scan != null) ScanSheet(scan, dependencies) {
                            // Resolves in the Live entry's own store, so leaving the scanner starts the next one fresh.
                            val live = viewModel(key = "live") {
                                LiveScanViewModel(dependencies.clock, onFinish = { scan.eventLoop(ScanCoordinator.Event.DidFinishLiveScan(it)) })
                            }
                            val back: () -> Unit = { scan.eventLoop(ScanCoordinator.Event.DidTapBack) }
                            LiveScannerScreen(live, dependencies.labels, onBack = back, onUsePhoto = back)
                        }
                    }
                    is SheetDestination.AddItem -> ItemFormEntry(ItemFormMode.Add, destination.draft, dependencies, onDone = dismiss)
                    is SheetDestination.EditItem -> ItemFormEntry(ItemFormMode.Edit(destination.itemId), null, dependencies, onDone = dismiss)
                    is SheetDestination.Cooked -> {
                        val cooked = viewModel {
                            CookedViewModel(destination.recipeId, dependencies.inventory, dependencies.settings, dependencies.recipes, dependencies.clock, onDone = dismiss)
                        }
                        CookedScreen(cooked)
                    }
                    SheetDestination.HowItWorks -> {
                        val replay = viewModel {
                            WelcomeViewModel(WelcomeMode.REPLAY, dependencies.settings, dependencies.notifications, onComplete = { dismiss() })
                        }
                        WelcomeScreen(replay)
                    }
                }
            }
        }
    }
}
