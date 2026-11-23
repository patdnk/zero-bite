package com.mazesys.zerobite.coordination

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

@Serializable
sealed interface RecipesRoute : NavKey {
    @Serializable
    data object Catalogue : RecipesRoute

    @Serializable
    data class RecipeDetail(val recipeId: String) : RecipesRoute

    @Serializable
    data class ItemDetail(val itemId: String) : RecipesRoute
}

/** The Recipes tab's back stack. Sheets (edit, cooked) belong to MainCoordinator. */
class RecipesCoordinator(private val parent: MainCoordinator?, scope: CoroutineScope) :
    EventDrivenCoordinator<RecipesCoordinator.State, RecipesCoordinator.Event, RecipesCoordinator.Command>(State(), RecipesCoordinator, scope) {

    data class State(val backStack: List<RecipesRoute> = listOf(RecipesRoute.Catalogue))

    sealed interface Event {
        data class DidSelectRecipe(val recipeId: String) : Event
        data class DidSelectItem(val itemId: String) : Event
        /** Back button, gesture or the system back. Never pops the catalogue itself. */
        data object DidTapBack : Event
        /** The item on screen was deleted: leave its page. */
        data object DidDeleteItem : Event
        data class DidTapEditItem(val itemId: String) : Event
        data class DidTapCooked(val recipeId: String) : Event
    }

    sealed interface Command {
        data class WillPresent(val destination: SheetDestination) : Command
    }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when (event) {
            is Event.DidSelectRecipe -> Step(state.copy(backStack = state.backStack + RecipesRoute.RecipeDetail(event.recipeId)))
            is Event.DidSelectItem -> Step(state.copy(backStack = state.backStack + RecipesRoute.ItemDetail(event.itemId)))
            Event.DidTapBack, Event.DidDeleteItem -> Step(state.copy(backStack = pop(state.backStack)))
            is Event.DidTapEditItem -> Step(state, listOf(Command.WillPresent(SheetDestination.EditItem(event.itemId))))
            is Event.DidTapCooked -> Step(state, listOf(Command.WillPresent(SheetDestination.Cooked(event.recipeId))))
        }
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        is Command.WillPresent -> CommandTask.Perform { parent?.eventLoop(MainCoordinator.Event.DidRequestSheet(command.destination)) }
    }
}
