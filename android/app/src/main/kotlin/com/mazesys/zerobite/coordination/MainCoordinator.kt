package com.mazesys.zerobite.coordination

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

enum class AppTab(@StringRes val title: Int) {
    FRIDGE(R.string.tab_fridge),
    RECIPES(R.string.tab_recipes),
    SETTINGS(R.string.tab_settings),
}

sealed interface SheetDestination {
    data object Scan : SheetDestination
    /** The onboarding explainer, replayed from Settings. */
    data object HowItWorks : SheetDestination
    data class AddItem(val draft: ItemDraft) : SheetDestination
    data class EditItem(val itemId: String) : SheetDestination
    data class Cooked(val recipeId: String) : SheetDestination
}

/** One presentation of a sheet; `id` is unique per presentation so its view models are fresh. */
data class PresentedSheet(val id: Long, val destination: SheetDestination)

/**
 * The tabs: which one is selected and the one sheet on top of them. Owns the three tab
 * coordinators and a ScanCoordinator per scan sheet. Started by AppCoordinator once the app reaches the main phase.
 */
class MainCoordinator(
    private val parent: AppCoordinator?,
    private val dependencies: AppDependencies,
    scope: CoroutineScope,
) : EventDrivenCoordinator<MainCoordinator.State, MainCoordinator.Event, MainCoordinator.Command>(State(), MainCoordinator, scope) {

    data class State(
        val selectedTab: AppTab = AppTab.FRIDGE,
        val sheet: PresentedSheet? = null,
        /** The id the next sheet gets; kept here so the state machine stays pure. */
        val nextSheetId: Long = 1,
    )

    sealed interface Event {
        data class DidSelectTab(val tab: AppTab) : Event
        /** A tab asked for a sheet. */
        data class DidRequestSheet(val destination: SheetDestination) : Event
        /** The sheet was dismissed with back, cancelled, saved or done. */
        data object DidDismissSheet : Event
        data object DidFinishScan : Event
        /** A notification was tapped: show that item on the Fridge tab. */
        data class DidRequestItem(val itemId: String) : Event
    }

    sealed interface Command {
        data object WillStartScan : Command
        data object WillStopScan : Command
        data class WillShowItem(val itemId: String) : Command
    }

    val inventory = InventoryCoordinator(parent = this, scope = scope)
    val recipes = RecipesCoordinator(parent = this, scope = scope)
    val settings = SettingsCoordinator(parent = this, inventory = dependencies.inventory, scope = scope)

    /**
     * The current scan sheet's coordinator; null until the first scan. A stopped one stays here,
     * inert, until the next scan starts, so its sheet can animate out.
     */
    var scan: ScanCoordinator? by mutableStateOf(null)
        private set

    /** The scan's own scope, so replacing a scan cancels whatever it left running. */
    private var scanScope: CoroutineScope? = null

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when (event) {
            is Event.DidSelectTab -> Step(state.copy(selectedTab = event.tab))

            is Event.DidRequestSheet ->
                // One sheet at a time.
                if (state.sheet != null) {
                    Step(state)
                } else {
                    Step(
                        state.copy(sheet = PresentedSheet(state.nextSheetId, event.destination), nextSheetId = state.nextSheetId + 1),
                        if (event.destination == SheetDestination.Scan) listOf(Command.WillStartScan) else emptyList(),
                    )
                }

            Event.DidDismissSheet -> closeSheet(state)

            Event.DidFinishScan ->
                if (state.sheet?.destination == SheetDestination.Scan) closeSheet(state) else Step(state)

            is Event.DidRequestItem -> {
                val closed = closeSheet(state.copy(selectedTab = AppTab.FRIDGE))
                closed.copy(commands = closed.commands + Command.WillShowItem(event.itemId))
            }
        }

        private fun closeSheet(state: State): Step<State, Command> {
            val wasScanning = state.sheet?.destination == SheetDestination.Scan
            return Step(state.copy(sheet = null), if (wasScanning) listOf(Command.WillStopScan) else emptyList())
        }
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        Command.WillStartScan -> CommandTask.Perform {
            scanScope?.cancel()
            val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
            scanScope = child
            scan = ScanCoordinator(
                parent = this,
                supportsLiveScanning = dependencies.labels.supportsLiveScanning,
                scanner = dependencies.labels,
                productLookup = dependencies.productLookup,
                scope = child,
            )
        }

        Command.WillStopScan -> CommandTask.Perform { scan?.eventLoop(ScanCoordinator.Event.DidDismiss) }

        is Command.WillShowItem -> CommandTask.Perform {
            // A deep link also closes anything Settings was asking.
            settings.eventLoop(SettingsCoordinator.Event.DidDismissAlert)
            inventory.eventLoop(InventoryCoordinator.Event.DidRequestItem(command.itemId))
        }
    }
}
