package com.mazesys.zerobite

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.core.time.SystemAppClock
import com.mazesys.zerobite.data.BundledRecipeRepository
import com.mazesys.zerobite.data.DataStoreSettings
import com.mazesys.zerobite.data.RoomInventoryRepository
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.data.ZeroBiteDatabase
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
    /** Outlives screens: background syncs (reminders, recalls) run here in Plan 3. */
    val appScope: CoroutineScope,
) {
    companion object {
        fun live(context: Context): AppDependencies {
            val app = context.applicationContext
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            return AppDependencies(
                clock = SystemAppClock,
                settings = DataStoreSettings(app.settingsDataStore, scope),
                inventory = RoomInventoryRepository(ZeroBiteDatabase.open(app).foodItems()),
                recipes = BundledRecipeRepository(app.assets),
                ids = IdGenerator.Random,
                appScope = scope,
            )
        }
    }
}
