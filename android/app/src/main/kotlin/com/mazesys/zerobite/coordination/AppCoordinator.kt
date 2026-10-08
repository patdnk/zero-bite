package com.mazesys.zerobite.coordination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.datetime.LocalDate

enum class AppTab(val title: String) {
    FRIDGE("Fridge"),
    RECIPES("Recipes"),
    SETTINGS("Settings"),
}

sealed interface SheetDestination {
    data object Scan : SheetDestination
    data class AddItem(val draft: ItemDraft) : SheetDestination
    data class EditItem(val itemId: String) : SheetDestination
}

/** One presentation of a sheet; `id` is unique per presentation so its view models are fresh. */
data class PresentedSheet(val id: Long, val destination: SheetDestination)

data class AlertButton(val title: String, val isDestructive: Boolean = false, val action: () -> Unit = {})

data class AlertConfig(
    val title: String,
    val message: String? = null,
    val primary: AlertButton,
    val secondary: AlertButton? = null,
)

/** Main-content coordinator: tabs, sheets, alerts and the feature coordinators. */
class AppCoordinator(val today: () -> LocalDate) {

    var selectedTab by mutableStateOf(AppTab.FRIDGE)
        private set
    var presentedSheet by mutableStateOf<PresentedSheet?>(null)
        private set
    var alert by mutableStateOf<AlertConfig?>(null)
        private set

    private var sheetCount = 0L

    val inventory = InventoryCoordinator(this)
    val recipes = RecipesCoordinator(this)

    fun selectTab(tab: AppTab) {
        selectedTab = tab
    }

    fun presentSheet(destination: SheetDestination) {
        presentedSheet = PresentedSheet(++sheetCount, destination)
    }

    fun dismissSheet() {
        presentedSheet = null
    }

    fun showAlert(config: AlertConfig) {
        alert = config
    }

    fun dismissAlert() {
        alert = null
    }

    /** Deep link (e.g. a notification tap): jump to the Fridge tab and open the item. */
    fun showItem(id: String) {
        presentedSheet = null
        selectedTab = AppTab.FRIDGE
        inventory.popToRoot()
        inventory.showItem(id)
    }
}
