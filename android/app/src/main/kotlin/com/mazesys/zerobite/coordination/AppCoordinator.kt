package com.mazesys.zerobite.coordination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mazesys.zerobite.AppDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The root of the coordinator tree: which root screen is shown (splash → welcome → main),
 * starting MainCoordinator, holding a notification tap until main is on screen, and refreshing
 * services when the app comes back to the foreground.
 */
class AppCoordinator(private val dependencies: AppDependencies, scope: CoroutineScope) :
    EventDrivenCoordinator<AppCoordinator.State, AppCoordinator.Event, AppCoordinator.Command>(State(), AppCoordinator, scope) {

    /** Which root screen is shown. */
    enum class Phase { LAUNCH, WELCOME, MAIN }

    data class State(
        val phase: Phase = Phase.LAUNCH,
        /** A notification was tapped before the tabs were on screen; open it once they are. */
        val pendingItemId: String? = null,
        /** Welcome is done and being saved; main starts once it is, so a relaunch can't show welcome again. */
        val isSavingWelcome: Boolean = false,
    )

    sealed interface Event {
        data class DidFinishLaunch(val hasCompletedWelcome: Boolean) : Event
        /** Onboarding done, with or without notifications. */
        data object DidFinishWelcome : Event
        data object DidRememberWelcomeCompleted : Event
        data class DidOpenItemNotification(val itemId: String) : Event
        data object DidBecomeActive : Event
    }

    sealed interface Command {
        data object WillRememberWelcomeCompleted : Command
        data object WillStartMain : Command
        data class WillShowItem(val itemId: String) : Command
        data object WillRefreshServices : Command
    }

    /** Started by WillStartMain; null until the app reaches the main phase. */
    var main: MainCoordinator? by mutableStateOf(null)
        private set

    /** The splash is done. Reads the stored flag here so the state machine stays pure. */
    fun finishLaunch(): Job = scope.launch {
        eventLoop(Event.DidFinishLaunch(dependencies.settings.current().hasCompletedWelcome)).join()
    }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when {
            state.phase == Phase.LAUNCH && event is Event.DidFinishLaunch ->
                if (event.hasCompletedWelcome) enterMain(state) else Step(state.copy(phase = Phase.WELCOME))

            state.phase == Phase.WELCOME && event == Event.DidFinishWelcome && !state.isSavingWelcome ->
                Step(state.copy(isSavingWelcome = true), listOf(Command.WillRememberWelcomeCompleted))

            state.phase == Phase.WELCOME && event == Event.DidRememberWelcomeCompleted -> enterMain(state)

            event is Event.DidOpenItemNotification ->
                if (state.phase == Phase.MAIN) Step(state, listOf(Command.WillShowItem(event.itemId)))
                else Step(state.copy(pendingItemId = event.itemId))

            event == Event.DidBecomeActive -> Step(state, listOf(Command.WillRefreshServices))

            else -> Step(state)
        }

        private fun enterMain(state: State): Step<State, Command> {
            val entered = state.copy(phase = Phase.MAIN, pendingItemId = null, isSavingWelcome = false)
            val pending = state.pendingItemId ?: return Step(entered, listOf(Command.WillStartMain))
            return Step(entered, listOf(Command.WillStartMain, Command.WillShowItem(pending)))
        }
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        Command.WillRememberWelcomeCompleted -> CommandTask.Run {
            dependencies.settings.update { it.copy(hasCompletedWelcome = true) }
            Event.DidRememberWelcomeCompleted
        }
        Command.WillStartMain -> CommandTask.Perform {
            if (main == null) main = MainCoordinator(parent = this, dependencies = dependencies, scope = scope)
        }
        is Command.WillShowItem -> CommandTask.Perform { main?.eventLoop(MainCoordinator.Event.DidRequestItem(command.itemId)) }
        // Notification permission may have been changed in system settings meanwhile.
        Command.WillRefreshServices -> CommandTask.Run {
            dependencies.reminders.resyncNow()
            null
        }
    }
}
