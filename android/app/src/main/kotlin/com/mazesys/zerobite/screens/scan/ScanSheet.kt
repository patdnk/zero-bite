package com.mazesys.zerobite.screens.scan

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanRoute
import com.mazesys.zerobite.screens.inventory.ItemFormMode
import com.mazesys.zerobite.screens.shared.ItemFormEntry

/**
 * The add-food sheet. Its ScanFlowViewModel (in the sheet's store) holds the ScanCoordinator, so
 * the stack lives as long as the sheet. `live` renders the camera screen (a slot so tests can stub it).
 */
@Composable
fun ScanSheet(
    dependencies: AppDependencies,
    onFinished: () -> Unit,
    live: @Composable (ScanFlowViewModel) -> Unit,
) {
    val flow = viewModel {
        ScanFlowViewModel(ScanCoordinator(onFinished), dependencies.labels.supportsLiveScanning, dependencies.labels, dependencies.clock)
    }
    val scan = flow.coordinator
    NavDisplay(
        backStack = scan.backStack,
        onBack = { scan.pop() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ScanRoute.Choose> { ScanFlowScreen(flow) }
            entry<ScanRoute.Live> { live(flow) }
            entry<ScanRoute.Review> { route ->
                val review = viewModel {
                    ScanReviewViewModel(route.result, dependencies.productLookup, dependencies.clock, onContinue = scan::showForm)
                }
                ScanReviewScreen(review, onBack = scan::pop)
            }
            entry<ScanRoute.Form> { route ->
                ItemFormEntry(ItemFormMode.Add, route.draft, dependencies, onDone = scan::finish)
            }
        },
    )
}
