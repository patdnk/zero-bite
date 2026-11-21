package com.mazesys.zerobite.screens.inventory

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.mazesys.zerobite.R
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.IdGenerator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.ui.text.formatQuantity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import java.util.Locale

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
    @StringRes val errorMessage: Int? = null,
    val isSaving: Boolean = false,
) {
    @get:StringRes val title: Int get() = if (isEditing) R.string.form_title_edit else R.string.form_title_new
    @get:StringRes val overline: Int get() = if (isEditing) R.string.form_overline_edit else R.string.form_overline_add
    @get:StringRes val saveTitle: Int get() = if (isEditing) R.string.form_save_edit else R.string.form_save_add
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
                quantityText = formatQuantity(draft.quantity, Locale.getDefault()),
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
                        it.copy(draft = draft, quantityText = formatQuantity(draft.quantity, Locale.getDefault()), isLoaded = true)
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
        val MISSING = R.string.form_missing
        val QUANTITY = Regex("""\d{1,6}([.,]\d{1,2})?""")
    }
}
