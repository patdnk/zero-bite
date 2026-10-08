package com.mazesys.zerobite.reminders

import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.notifications.ExpiringItemSummary
import com.mazesys.zerobite.core.notifications.ExpiryNotificationPlanner
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.SettingsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-plans every reminder from scratch whenever the fridge or settings change, so the
 * scheduled set always mirrors the fridge (as iOS does on every inventory change).
 */
class ReminderSync(
    private val inventory: InventoryRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
    private val access: NotificationAccess,
    private val scheduler: ReminderScheduler,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        combine(inventory.items, settings.state) { items, s -> items to s }.collectLatest { (items, s) -> sync(items, s) }
    }

    /** After a reboot, app update, time change, or returning to the app (permission may have changed). */
    suspend fun resyncNow() = sync(inventory.items.first(), settings.current())

    private fun sync(items: List<FoodItem>, settings: SettingsSnapshot) {
        val allowed = settings.notificationsEnabled && access.permission() == NotificationPermission.GRANTED
        val summaries = items.filterNot { it.isConsumed }.map { ExpiringItemSummary(it.id, it.name, it.expiresOn) }
        val preferences = settings.notificationPreferences.copy(enabled = allowed)
        scheduler.reschedule(ExpiryNotificationPlanner(clock.timeZone).plan(summaries, preferences, clock.now()))
    }
}
