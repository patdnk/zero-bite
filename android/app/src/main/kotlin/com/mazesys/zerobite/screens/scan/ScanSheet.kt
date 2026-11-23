package com.mazesys.zerobite.screens.scan

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanCoordinator.Event
import com.mazesys.zerobite.coordination.ScanRoute
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.shared.ItemFormEntry

/**
 * The add-food sheet, driven by the ScanCoordinator MainCoordinator started for it. `live` renders
 * the camera screen (a slot so tests can stub it).
 */
@Composable
fun ScanSheet(
    coordinator: ScanCoordinator,
    dependencies: AppDependencies,
    live: @Composable (ScanCoordinator) -> Unit,
) {
    NavDisplay(
        backStack = coordinator.state.backStack,
        onBack = { coordinator.eventLoop(Event.DidTapBack) },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ScanRoute.Choose> { ScanFlowScreen(coordinator, dependencies.clock) }
            entry<ScanRoute.Live> { live(coordinator) }
            entry<ScanRoute.Review> { ScanReviewScreen(coordinator, dependencies.clock) }
            entry<ScanRoute.Form> { route ->
                ItemFormEntry(
                    ItemFormMode.Add, route.draft, dependencies,
                    onDone = { coordinator.eventLoop(Event.DidSaveItem) },
                    onCancel = { coordinator.eventLoop(Event.DidTapClose) },
                )
            }
        },
    )
}
