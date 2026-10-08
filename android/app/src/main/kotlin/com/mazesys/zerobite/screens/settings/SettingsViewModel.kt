package com.mazesys.zerobite.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.SettingsCoordinator
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.reminders.PermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ReminderState {
    ON,
    OFF,
    /** Refused at the system level; only system settings can change it. */
    DENIED,
}

data class SettingsUiState(
    val appVersion: String,
    val snapshot: SettingsSnapshot = SettingsSnapshot(),
    val permission: NotificationPermission = NotificationPermission.NOT_ASKED,
    val activeCount: Int = 0,
    val usedCount: Int = 0,
    val recipeCount: Int = 0,
    val isUpdatingNotifications: Boolean = false,
) {
    val reminderState: ReminderState
        get() = when {
            snapshot.notificationsEnabled && permission == NotificationPermission.GRANTED -> ReminderState.ON
            permission == NotificationPermission.DENIED -> ReminderState.DENIED
            else -> ReminderState.OFF
        }

    val notificationsEnabled: Boolean get() = reminderState == ReminderState.ON
    val notifyTimeText: String get() = "%02d:%02d".format(snapshot.notifyHour, snapshot.notifyMinute)
}

class SettingsViewModel(
    private val dependencies: AppDependencies,
    private val coordinator: SettingsCoordinator,
    appVersion: String,
) : ViewModel() {

    private val settings = dependencies.settings
    private val inventory = dependencies.inventory
    private val permission = MutableStateFlow(dependencies.notifications.permission())
    private val updating = MutableStateFlow(false)
    private val recipeCount = flow { emit(dependencies.recipes.recipes().size) }

    val state: StateFlow<SettingsUiState> =
        combine(settings.state, inventory.items, recipeCount, permission, updating) { s, items, recipes, p, busy ->
            SettingsUiState(
                appVersion = appVersion,
                snapshot = s,
                permission = p,
                activeCount = items.count { !it.isConsumed },
                usedCount = items.count { it.isConsumed },
                recipeCount = recipes,
                isUpdatingNotifications = busy,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(appVersion))

    /** Call when the screen resumes: the permission may have changed in system settings. */
    fun refreshPermission() {
        permission.value = dependencies.notifications.permission()
    }

    fun setNotificationsEnabled(enabled: Boolean, requester: PermissionRequester) {
        viewModelScope.launch {
            updating.value = true
            if (enabled) {
                val granted = requester.requestNotifications()
                dependencies.notifications.markAsked()
                refreshPermission()
                settings.update { it.copy(notificationsEnabled = granted) }
                if (!granted) {
                    coordinator.showInfo(
                        "Notifications are off",
                        "Allow notifications for Zero Bite in your phone's settings to get expiry reminders.",
                    )
                }
            } else {
                settings.update { it.copy(notificationsEnabled = false) }
            }
            updating.value = false
        }
    }

    fun setLeadDays(days: Int) = update { it.copy(leadDays = days.coerceIn(1, 14)) }

    fun setNotifyTime(hour: Int, minute: Int) = update { it.copy(notifyHour = hour.coerceIn(0, 23), notifyMinute = minute.coerceIn(0, 59)) }

    fun setSoonThresholdDays(days: Int) = update { it.copy(soonThresholdDays = days.coerceIn(1, 14)) }

    fun setRecallChecksEnabled(enabled: Boolean) = update { it.copy(recallChecksEnabled = enabled) }

    fun toggleAllergen(allergen: Allergen) = update {
        it.copy(allergens = if (allergen.raw in it.allergens) it.allergens - allergen.raw else it.allergens + allergen.raw)
    }

    fun sendTest() = dependencies.notifier.sendTest()

    fun loadSampleData() {
        viewModelScope.launch {
            val clock = dependencies.clock
            SampleData.items(clock.today(), clock.now(), dependencies.ids::next).forEach { inventory.add(it) }
        }
    }

    fun clearUsedHistory() {
        viewModelScope.launch {
            val used = inventory.items.first().filter { it.isConsumed }
            if (used.isEmpty()) return@launch
            coordinator.confirm("Clear used-up history?", "Removes ${used.size} items you've already marked as used.", "Clear") {
                viewModelScope.launch { used.forEach { inventory.delete(it.id) } }
            }
        }
    }

    fun deleteEverything() {
        coordinator.confirm("Delete everything?", "This removes every item from your fridge. It can't be undone.", "Delete all") {
            viewModelScope.launch { inventory.deleteAll() }
        }
    }

    fun showHowItWorks() = coordinator.showHowItWorks()

    private fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        viewModelScope.launch { settings.update(transform) }
    }
}
