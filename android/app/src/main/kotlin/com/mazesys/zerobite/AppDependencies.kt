package com.mazesys.zerobite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.notifications.NotificationCopy
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.core.time.SystemAppClock
import com.mazesys.zerobite.data.AppLanguage
import com.mazesys.zerobite.data.BundledRecipeRepository
import com.mazesys.zerobite.data.DataStoreSettings
import com.mazesys.zerobite.data.RoomInventoryRepository
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.ZeroBiteDatabase
import com.mazesys.zerobite.reminders.AlarmReminderScheduler
import com.mazesys.zerobite.reminders.AndroidNotificationAccess
import com.mazesys.zerobite.reminders.AndroidNotifier
import com.mazesys.zerobite.reminders.NotificationAccess
import com.mazesys.zerobite.reminders.Notifier
import com.mazesys.zerobite.reminders.ReminderScheduler
import com.mazesys.zerobite.reminders.ResourceNotificationCopy
import com.mazesys.zerobite.reminders.ReminderSync
import com.mazesys.zerobite.ui.text.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.uuid.Uuid

fun interface IdGenerator {
    fun next(): String

    companion object {
        val Random = IdGenerator { Uuid.random().toString() }
    }
}

/** One DataStore per process, as DataStore requires. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Composition root: the long-lived services handed to coordinators, factories and view models. */
class AppDependencies(
    val clock: AppClock,
    val settings: SettingsRepository,
    val inventory: InventoryRepository,
    val recipes: RecipeRepository,
    val ids: IdGenerator,
    /** Outlives screens: background syncs (reminders, recalls) run here. */
    val appScope: CoroutineScope,
    val notifications: NotificationAccess,
    val scheduler: ReminderScheduler,
    val notifier: Notifier,
    /** String resources in the current app language, for text that is stored rather than shown. */
    val strings: Strings,
    /** The current app language, kept up to date by MainActivity. */
    val language: AppLanguage = AppLanguage(),
    /** Reminder text in the current app language, read at every replan. */
    notificationCopy: () -> NotificationCopy = { NotificationCopy.ENGLISH },
) {
    val reminders = ReminderSync(inventory, settings, clock, notifications, scheduler, notificationCopy)

    companion object {
        fun live(context: Context): AppDependencies {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val language = AppLanguage(AppLanguage.of(app.resources.configuration))
            return AppDependencies(
                clock = SystemAppClock,
                settings = DataStoreSettings(app.settingsDataStore, scope),
                inventory = RoomInventoryRepository(ZeroBiteDatabase.open(app).foodItems()),
                recipes = BundledRecipeRepository(app.assets, language.code),
                ids = IdGenerator.Random,
                appScope = scope,
                notifications = AndroidNotificationAccess(app, prefs),
                scheduler = AlarmReminderScheduler(app, prefs),
                notifier = AndroidNotifier(app),
                strings = Strings { app.getString(it) },
                language = language,
                notificationCopy = { ResourceNotificationCopy(app.resources) },
            )
        }
    }
}
