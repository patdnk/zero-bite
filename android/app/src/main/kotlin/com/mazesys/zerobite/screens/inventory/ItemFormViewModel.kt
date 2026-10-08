package com.mazesys.zerobite.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.formatQuantity
import com.mazesys.zerobite.core.time.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

sealed interface ItemFormMode {
    data object Add : ItemFormMode
    data class Edit(val itemId: String) : ItemFormMode
}

data class ItemFormUiState(
    val draft: ItemDraft,
    /** What the user typed in the amount field; `draft.quantity` is its parsed value. */
    val quantityText: String,
    val isEditing: Boolean,
    val isLoaded: Boolean = true,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val title: String get() = if (isEditing) "Edit item" else "New item"
    val overline: String get() = if (isEditing) "Edit" else "Add to your fridge"
    val saveTitle: String get() = if (isEditing) "Save changes" else "Add to fridge"
    val canSave: Boolean get() = isLoaded && draft.isValid && !isSaving
    val defaultShelfLifeDays: Int get() = draft.category.defaultShelfLifeDays
}

class ItemFormViewModel(
    private val mode: ItemFormMode,
    initialDraft: ItemDraft?,
    private val inventory: InventoryRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val onSaved: (FoodItem) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(
        (initialDraft ?: ItemDraft.new(clock.today())).let { draft ->
            ItemFormUiState(
                draft = draft,
                quantityText = formatQuantity(draft.quantity),
                isEditing = mode is ItemFormMode.Edit,
                isLoaded = initialDraft != null || mode is ItemFormMode.Add,
            )
        },
    )
    val state: StateFlow<ItemFormUiState> = _state.asStateFlow()

    init {
        if (mode is ItemFormMode.Edit && initialDraft == null) {
            viewModelScope.launch {
                val existing = inventory.item(mode.itemId)
                _state.update {
                    if (existing == null) {
                        it.copy(isLoaded = true, errorMessage = MISSING)
                    } else {
                        val draft = ItemDraft.from(existing)
                        it.copy(draft = draft, quantityText = formatQuantity(draft.quantity), isLoaded = true)
                    }
                }
            }
        }
    }

    fun updateDraft(transform: (ItemDraft) -> ItemDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }

    /**
     * Plain decimals only ("2", "0.25", "1,5"): up to six digits and two decimal places. Anything
     * else (float syntax, "1,000", amounts that round to zero) counts as zero, which can't be saved.
     */
    fun setQuantityText(text: String) {
        val trimmed = text.trim()
        val parsed = if (QUANTITY.matches(trimmed)) trimmed.replace(',', '.').toDouble() else 0.0
        _state.update { it.copy(quantityText = text, draft = it.draft.copy(quantity = parsed)) }
    }

    /** Sets the expiry from the category's typical shelf life (for labels with no readable date). */
    fun applyDefaultShelfLife() {
        updateDraft { it.copy(expiresOn = clock.today().plus(it.category.defaultShelfLifeDays, DateTimeUnit.DAY)) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (mode) {
                ItemFormMode.Add -> {
                    val item = current.draft.makeItem(ids.next(), clock.now())
                    inventory.add(item)
                    onSaved(item)
                }
                is ItemFormMode.Edit -> {
                    val existing = inventory.item(mode.itemId)
                    if (existing == null) {
                        _state.update { it.copy(isSaving = false, errorMessage = MISSING) }
                        return@launch
                    }
                    val updated = current.draft.applyTo(existing)
                    inventory.update(updated)
                    onSaved(updated)
                }
            }
        }
    }

    private companion object {
        const val MISSING = "This item no longer exists."
        val QUANTITY = Regex("""\d{1,6}([.,]\d{1,2})?""")
    }
}
