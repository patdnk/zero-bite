package com.mazesys.zerobite.coordination

import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
sealed interface InventoryRoute : NavKey {
    @Serializable
    data object Fridge : InventoryRoute

    @Serializable
    data class ItemDetail(val itemId: String) : InventoryRoute

    @Serializable
    data class RecipeDetail(val recipeId: String) : InventoryRoute
}

/**
 * The Fridge tab's back stack (the Android counterpart of iOS's NavigationPath). Sheets belong to
 * MainCoordinator, so this asks for them with DidRequestSheet.
 */
class InventoryCoordinator(private val parent: MainCoordinator?, scope: CoroutineScope) :
    EventDrivenCoordinator<InventoryCoordinator.State, InventoryCoordinator.Event, InventoryCoordinator.Command>(State(), InventoryCoordinator, scope) {

    data class State(val backStack: List<InventoryRoute> = listOf(InventoryRoute.Fridge))

    sealed interface Event {
        data class DidSelectItem(val itemId: String) : Event
        data class DidSelectRecipe(val recipeId: String) : Event
        /** Back button, gesture or the system back. Never pops the list itself. */
        data object DidTapBack : Event
        /** The item on screen was deleted: leave its page. */
        data object DidDeleteItem : Event
        /** A notification was tapped: show just that item over the list. */
        data class DidRequestItem(val itemId: String) : Event
        data object DidTapScan : Event
        data class DidTapAddItem(val today: LocalDate) : Event
        data class DidTapEditItem(val itemId: String) : Event
        data class DidTapCooked(val recipeId: String) : Event
    }

    sealed interface Command {
        data class WillPresent(val destination: SheetDestination) : Command
    }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when (event) {
            is Event.DidSelectItem -> Step(state.copy(backStack = state.backStack + InventoryRoute.ItemDetail(event.itemId)))
            is Event.DidSelectRecipe -> Step(state.copy(backStack = state.backStack + InventoryRoute.RecipeDetail(event.recipeId)))
            Event.DidTapBack, Event.DidDeleteItem -> Step(state.copy(backStack = pop(state.backStack)))
            is Event.DidRequestItem -> Step(state.copy(backStack = listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail(event.itemId))))
            Event.DidTapScan -> present(state, SheetDestination.Scan)
            is Event.DidTapAddItem -> present(state, SheetDestination.AddItem(ItemDraft.new(event.today)))
            is Event.DidTapEditItem -> present(state, SheetDestination.EditItem(event.itemId))
            is Event.DidTapCooked -> present(state, SheetDestination.Cooked(event.recipeId))
        }

        private fun present(state: State, destination: SheetDestination) = Step<State, Command>(state, listOf(Command.WillPresent(destination)))
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        is Command.WillPresent -> CommandTask.Perform { parent?.eventLoop(MainCoordinator.Event.DidRequestSheet(command.destination)) }
    }
}

/** Drops the top page, never the root. */
internal fun <T> pop(backStack: List<T>): List<T> = if (backStack.size > 1) backStack.dropLast(1) else backStack
