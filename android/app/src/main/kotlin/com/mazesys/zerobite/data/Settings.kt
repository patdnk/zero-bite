package com.mazesys.zerobite.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mazesys.zerobite.core.notifications.NotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** User preferences, with the iOS defaults. */
data class SettingsSnapshot(
    /** First-run welcome (intro + notification opt-in) has been seen. */
    val hasCompletedWelcome: Boolean = false,
    val notificationsEnabled: Boolean = false,
    /** Days before expiry for the first reminder. */
    val leadDays: Int = NotificationPreferences.DEFAULT.leadDays,
    val notifyHour: Int = NotificationPreferences.DEFAULT.hour,
    val notifyMinute: Int = NotificationPreferences.DEFAULT.minute,
    /** Items expiring within this many days count as "expiring soon". */
    val soonThresholdDays: Int = 3,
    /** The Fridge "turn on reminders" tip was answered. */
    val dismissedNotificationTip: Boolean = false,
    /** Check the Food Standards Agency for recalls and allergy alerts. */
    val recallChecksEnabled: Boolean = true,
    /** Allergen raw values the user wants allergy alerts for. */
    val allergens: Set<String> = emptySet(),
    /** Consent to send usage statistics and crash reports (Firebase). Off until the user agrees. */
    val shareUsageData: Boolean = false,
) {
    val notificationPreferences: NotificationPreferences
        get() = NotificationPreferences(notificationsEnabled, leadDays, notifyHour, notifyMinute)
}

interface SettingsRepository {
    /** Live settings. Before the first read it holds the defaults, so use [current] for decisions. */
    val state: StateFlow<SettingsSnapshot>
    /** Settings as saved: like [state], but starting from the first read rather than the defaults. */
    val saved: Flow<SettingsSnapshot>
    suspend fun current(): SettingsSnapshot
    suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot)
}

class DataStoreSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) : SettingsRepository {

    override val saved: Flow<SettingsSnapshot> = store.data.map(::read)

    override val state: StateFlow<SettingsSnapshot> = saved.stateIn(scope, SharingStarted.Eagerly, SettingsSnapshot())

    override suspend fun current(): SettingsSnapshot = read(store.data.first())

    override suspend fun update(transform: (SettingsSnapshot) -> SettingsSnapshot) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    /** Same key names as iOS `AppSettings`. */
    private object Keys {
        val hasCompletedWelcome = booleanPreferencesKey("hasCompletedWelcome")
        val notificationsEnabled = booleanPreferencesKey("notificationsEnabled")
        val leadDays = intPreferencesKey("notificationLeadDays")
        val notifyHour = intPreferencesKey("notifyHour")
        val notifyMinute = intPreferencesKey("notifyMinute")
        val soonThresholdDays = intPreferencesKey("soonThresholdDays")
        val dismissedNotificationTip = booleanPreferencesKey("dismissedNotificationTip")
        val recallChecksEnabled = booleanPreferencesKey("recallChecksEnabled")
        val allergens = stringSetPreferencesKey("allergens")
        val shareUsageData = booleanPreferencesKey("shareUsageData")
    }

    private fun read(prefs: Preferences): SettingsSnapshot {
        val defaults = SettingsSnapshot()
        return SettingsSnapshot(
            hasCompletedWelcome = prefs[Keys.hasCompletedWelcome] ?: defaults.hasCompletedWelcome,
            notificationsEnabled = prefs[Keys.notificationsEnabled] ?: defaults.notificationsEnabled,
            leadDays = prefs[Keys.leadDays] ?: defaults.leadDays,
            notifyHour = prefs[Keys.notifyHour] ?: defaults.notifyHour,
            notifyMinute = prefs[Keys.notifyMinute] ?: defaults.notifyMinute,
            soonThresholdDays = prefs[Keys.soonThresholdDays] ?: defaults.soonThresholdDays,
            dismissedNotificationTip = prefs[Keys.dismissedNotificationTip] ?: defaults.dismissedNotificationTip,
            recallChecksEnabled = prefs[Keys.recallChecksEnabled] ?: defaults.recallChecksEnabled,
            allergens = prefs[Keys.allergens] ?: defaults.allergens,
            shareUsageData = prefs[Keys.shareUsageData] ?: defaults.shareUsageData,
        )
    }

    private fun write(prefs: MutablePreferences, s: SettingsSnapshot) {
        prefs[Keys.hasCompletedWelcome] = s.hasCompletedWelcome
        prefs[Keys.notificationsEnabled] = s.notificationsEnabled
        prefs[Keys.leadDays] = s.leadDays
        prefs[Keys.notifyHour] = s.notifyHour
        prefs[Keys.notifyMinute] = s.notifyMinute
        prefs[Keys.soonThresholdDays] = s.soonThresholdDays
        prefs[Keys.dismissedNotificationTip] = s.dismissedNotificationTip
        prefs[Keys.recallChecksEnabled] = s.recallChecksEnabled
        prefs[Keys.allergens] = s.allergens
        prefs[Keys.shareUsageData] = s.shareUsageData
    }
}
