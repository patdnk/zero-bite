package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.ItemDraft
import kotlinx.serialization.Serializable

@Serializable
sealed interface InventoryRoute : NavKey {
    @Serializable
    data object Fridge : InventoryRoute

    @Serializable
    data class ItemDetail(val itemId: String) : InventoryRoute
}

/** Navigation within the Fridge tab. The back stack is the Android counterpart of iOS's NavigationPath. */
class InventoryCoordinator(private val parent: AppCoordinator) {

    val backStack: SnapshotStateList<InventoryRoute> = mutableStateListOf(InventoryRoute.Fridge)

    fun showItem(id: String) {
        backStack.add(InventoryRoute.ItemDetail(id))
    }

    fun showScan() = parent.presentSheet(SheetDestination.Scan)

    fun showAddItem() = parent.presentSheet(SheetDestination.AddItem(ItemDraft.new(parent.today())))

    fun showEditItem(id: String) = parent.presentSheet(SheetDestination.EditItem(id))

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}
