package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.ui.components.shelfLifeProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

enum class DetailState { LOADING, ACTIVE, USED, MISSING }

data class ItemDetailUiState(
    val today: LocalDate,
    val state: DetailState = DetailState.LOADING,
    val item: FoodItem? = null,
    val soonThresholdDays: Int = 3,
    val progress: Float = 0f,
    val addedOn: LocalDate? = null,
    val consumedOn: LocalDate? = null,
) {
    val status: ExpiryStatus? get() = item?.status(today, soonThresholdDays)
}

class ItemDetailViewModel(
    val itemId: String,
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    private val clock: AppClock,
    private val onEdit: (String) -> Unit,
    private val onDeleted: () -> Unit,
) : ViewModel() {

    val state: StateFlow<ItemDetailUiState> =
        combine(inventory.items, settings.state) { items, s ->
            val item = items.firstOrNull { it.id == itemId }
            ItemDetailUiState(
                today = clock.today(),
                state = when {
                    item == null -> DetailState.MISSING
                    item.isConsumed -> DetailState.USED
                    else -> DetailState.ACTIVE
                },
                item = item,
                soonThresholdDays = s.soonThresholdDays,
                progress = item?.shelfLifeProgress(clock.now(), clock.timeZone) ?: 0f,
                addedOn = item?.addedAt?.toLocalDateTime(clock.timeZone)?.date,
                consumedOn = item?.consumedAt?.toLocalDateTime(clock.timeZone)?.date,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ItemDetailUiState(today = clock.today()))

    fun markUsed() {
        viewModelScope.launch { inventory.markConsumed(listOf(itemId), clock.now()) }
    }

    fun restore() {
        viewModelScope.launch { inventory.restore(itemId) }
    }

    fun delete() {
        viewModelScope.launch {
            inventory.delete(itemId)
            onDeleted()
        }
    }

    fun edit() = onEdit(itemId)
}
