package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.uiPlural
import com.mazesys.zerobite.ui.text.uiText
import kotlinx.coroutines.CoroutineScope

/** What Settings is asking or telling. Its text is resources, resolved where it is shown. */
sealed interface SettingsAlert {
    data object NotificationsOff : SettingsAlert

    /** The used-up items as they were when the user asked, so "Clear" removes exactly the ones the message counted. */
    data class ConfirmClearUsedHistory(val itemIds: List<String>) : SettingsAlert

    data object ConfirmDeleteEverything : SettingsAlert

    val title: UiText
        get() = when (this) {
            NotificationsOff -> uiText(R.string.alert_notifications_off_title)
            is ConfirmClearUsedHistory -> uiText(R.string.alert_clear_history_title)
            ConfirmDeleteEverything -> uiText(R.string.alert_delete_everything_title)
        }

    val message: UiText
        get() = when (this) {
            NotificationsOff -> uiText(R.string.alert_notifications_off_message)
            is ConfirmClearUsedHistory -> uiPlural(R.plurals.alert_clear_history_message, itemIds.size)
            ConfirmDeleteEverything -> uiText(R.string.alert_delete_everything_message)
        }

    /** The destructive action a confirmation asks about; null for an info message. */
    val actionTitle: UiText?
        get() = when (this) {
            NotificationsOff -> null
            is ConfirmClearUsedHistory -> uiText(R.string.alert_clear_history_action)
            ConfirmDeleteEverything -> uiText(R.string.alert_delete_everything_action)
        }
}

/** The Settings tab: its own alerts and confirmations, and carrying out what a confirmation confirms. */
class SettingsCoordinator(
    private val parent: MainCoordinator?,
    private val inventory: InventoryRepository,
    scope: CoroutineScope,
) : EventDrivenCoordinator<SettingsCoordinator.State, SettingsCoordinator.Event, SettingsCoordinator.Command>(State(), SettingsCoordinator, scope) {

    data class State(val alert: SettingsAlert? = null)

    sealed interface Event {
        /** The user turned reminders on but the system permission was refused. */
        data object DidFindNotificationsDenied : Event
        data class DidTapClearUsedHistory(val itemIds: List<String>) : Event
        data object DidTapDeleteEverything : Event
        /** Carries the alert itself, so it acts even if the dialog was already cleared. */
        data class DidConfirmAlert(val alert: SettingsAlert) : Event
        data object DidDismissAlert : Event
        data object DidTapHowItWorks : Event
    }

    sealed interface Command {
        data class WillClearUsedHistory(val itemIds: List<String>) : Command
        data object WillDeleteEverything : Command
        data object WillShowHowItWorks : Command
    }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when (event) {
            Event.DidFindNotificationsDenied -> show(state, SettingsAlert.NotificationsOff)
            is Event.DidTapClearUsedHistory ->
                if (event.itemIds.isEmpty()) Step(state) else show(state, SettingsAlert.ConfirmClearUsedHistory(event.itemIds))
            Event.DidTapDeleteEverything -> show(state, SettingsAlert.ConfirmDeleteEverything)
            is Event.DidConfirmAlert -> Step(
                state.copy(alert = null),
                when (val alert = event.alert) {
                    SettingsAlert.NotificationsOff -> emptyList()
                    is SettingsAlert.ConfirmClearUsedHistory -> listOf(Command.WillClearUsedHistory(alert.itemIds))
                    SettingsAlert.ConfirmDeleteEverything -> listOf(Command.WillDeleteEverything)
                },
            )
            Event.DidDismissAlert -> Step(state.copy(alert = null))
            Event.DidTapHowItWorks -> Step(state, listOf(Command.WillShowHowItWorks))
        }

        /** One alert at a time. */
        private fun show(state: State, alert: SettingsAlert): Step<State, Command> =
            if (state.alert != null) Step(state) else Step(state.copy(alert = alert))
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        is Command.WillClearUsedHistory -> CommandTask.Run {
            command.itemIds.forEach { inventory.delete(it) }
            null
        }
        Command.WillDeleteEverything -> CommandTask.Run {
            inventory.deleteAll()
            null
        }
        Command.WillShowHowItWorks -> CommandTask.Perform { parent?.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.HowItWorks)) }
    }
}
