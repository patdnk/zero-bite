package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SampleData
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.ui.components.shelfLifeProgress
import com.mazesys.zerobite.ui.format.DateText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class StorageFilter(val title: String, val location: StorageLocation?) {
    ALL("All", null),
    FRIDGE("Fridge", StorageLocation.FRIDGE),
    FREEZER("Freezer", StorageLocation.FREEZER),
    PANTRY("Cupboard", StorageLocation.PANTRY),
}

data class InventorySection(val status: ExpiryStatus, val items: List<FoodItem>)

data class InventoryUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val headerDate: String = DateText.header(today),
    /** Items not yet used up, soonest expiry first. */
    val activeItems: List<FoodItem> = emptyList(),
    val searchText: String = "",
    val storageFilter: StorageFilter = StorageFilter.ALL,
    val soonThresholdDays: Int = 3,
    /** The single most urgent item, highlighted above the grid while browsing (not searching). */
    val useFirstItem: FoodItem? = null,
    val useFirstProgress: Float = 0f,
    val attentionCount: Int = 0,
    /** Sections below the hero card, which already shows its item. */
    val gridSections: List<InventorySection> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && activeItems.isEmpty()

    /** Items needing attention other than the highlighted one. */
    val othersNeedingAttention: Int get() = maxOf(attentionCount - if (useFirstItem == null) 0 else 1, 0)

    val emptyGridMessage: String
        get() = if (searchText.isBlank()) "Nothing stored here." else "Nothing matches “$searchText”."

    /** Freezing only makes sense for food that hasn't gone off and isn't frozen already. */
    fun canFreeze(item: FoodItem): Boolean =
        item.storage != StorageLocation.FREEZER && item.status(today, soonThresholdDays) != ExpiryStatus.EXPIRED

    companion object {
        fun build(
            active: List<FoodItem>,
            searchText: String,
            filter: StorageFilter,
            soonThresholdDays: Int,
            today: LocalDate,
        ): InventoryUiState {
            val query = searchText.trim().lowercase()
            val filtered = active.filter { item ->
                (filter.location == null || item.storage == filter.location) &&
                    (query.isEmpty() || item.name.lowercase().contains(query))
            }
            fun needsAttention(item: FoodItem) = item.status(today, soonThresholdDays).needsAttention
            val hero = if (query.isNotEmpty()) null else filtered.filter(::needsAttention).minByOrNull { it.expiresOn }
            val grid = filtered.filter { it.id != hero?.id }
            val sections = ExpiryStatus.entries.mapNotNull { status ->
                grid.filter { it.status(today, soonThresholdDays) == status }
                    .takeIf { it.isNotEmpty() }
                    ?.let { InventorySection(status, it) }
            }
            return InventoryUiState(
                today = today,
                isLoading = false,
                activeItems = active,
                searchText = searchText,
                storageFilter = filter,
                soonThresholdDays = soonThresholdDays,
                useFirstItem = hero,
                attentionCount = active.count(::needsAttention),
                gridSections = sections,
            )
        }
    }
}

/** The Fridge list: filtering, grouping by urgency, and item actions. */
class InventoryViewModel(
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val coordinator: InventoryCoordinator,
) : ViewModel() {

    private val searchText = MutableStateFlow("")
    private val storageFilter = MutableStateFlow(StorageFilter.ALL)

    val state: StateFlow<InventoryUiState> =
        combine(inventory.items, settings.state, searchText, storageFilter) { items, s, query, filter ->
            val built = InventoryUiState.build(items.filterNot { it.isConsumed }, query, filter, s.soonThresholdDays, clock.today())
            built.copy(useFirstProgress = built.useFirstItem?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryUiState(today = clock.today()))

    fun setSearchText(text: String) {
        searchText.value = text
    }

    fun setStorageFilter(filter: StorageFilter) {
        storageFilter.value = filter
    }

    fun markUsed(item: FoodItem) {
        viewModelScope.launch { inventory.markConsumed(listOf(item.id), clock.now()) }
    }

    /** Moves an item to the freezer with a typical frozen shelf life of three months. */
    fun freeze(item: FoodItem) {
        if (!state.value.canFreeze(item)) return
        val frozenUntil = clock.today().plus(3, DateTimeUnit.MONTH)
        viewModelScope.launch {
            inventory.update(item.copy(storage = StorageLocation.FREEZER, expiresOn = maxOf(item.expiresOn, frozenUntil)))
        }
    }

    fun delete(item: FoodItem) {
        viewModelScope.launch { inventory.delete(item.id) }
    }

    fun showDetails(item: FoodItem) = coordinator.showItem(item.id)

    fun showScan() = coordinator.showScan()

    fun showAddItem() = coordinator.showAddItem()

    fun showEditItem(item: FoodItem) = coordinator.showEditItem(item.id)

    fun loadSampleData() {
        viewModelScope.launch {
            SampleData.items(clock.today(), clock.now(), ids::next).forEach { inventory.add(it) }
        }
    }
}
