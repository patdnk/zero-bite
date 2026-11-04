package com.mazesys.zerobite.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.products.ProductLookup
import com.mazesys.zerobite.core.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

data class ScanReviewUiState(
    val result: ScanResult,
    val name: String,
    val category: FoodCategory = FoodCategory.OTHER,
    /** Index into `candidates`; null for "None of these". */
    val selectedIndex: Int? = null,
    val product: ProductInfo? = null,
    val isLookingUp: Boolean = false,
    val showsLines: Boolean = false,
) {
    val candidates: List<DateCandidate> get() = result.candidates
    val lines: List<String> get() = result.lines
    val barcode: String? get() = result.barcode
    val selectedCandidate: DateCandidate? get() = selectedIndex?.let { candidates.getOrNull(it) }
    val canContinue: Boolean get() = name.isNotBlank()
}

/** Confirm what was read: product name (barcode lookup when possible) and which date is the expiry. */
class ScanReviewViewModel(
    result: ScanResult,
    private val productLookup: ProductLookup,
    private val clock: AppClock,
    private val onContinue: (ItemDraft) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScanReviewUiState(result, name = result.suggestedName ?: "", selectedIndex = if (result.candidates.isEmpty()) null else 0),
    )
    val state: StateFlow<ScanReviewUiState> = _state.asStateFlow()

    init {
        loadProduct()
    }

    private fun loadProduct() {
        val result = _state.value.result
        val barcode = result.barcode ?: return
        _state.update { it.copy(isLookingUp = true) }
        viewModelScope.launch {
            val info = try {
                productLookup.lookup(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // Offline, timed out or an unreadable reply: keep what OCR found.
            }
            _state.update { s ->
                if (info == null) {
                    s.copy(isLookingUp = false)
                } else {
                    s.copy(
                        isLookingUp = false,
                        product = info,
                        // A catalogue name beats an OCR guess; keep anything the user has already typed.
                        name = if (s.name.isEmpty() || s.name == result.suggestedName) info.name else s.name,
                        category = info.category ?: s.category,
                    )
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setCategory(category: FoodCategory) = _state.update { it.copy(category = category) }

    fun select(index: Int) = _state.update { it.copy(selectedIndex = index) }

    fun clearSelection() = _state.update { it.copy(selectedIndex = null) }

    fun toggleLines() = _state.update { it.copy(showsLines = !it.showsLines) }

    fun makeDraft(): ItemDraft {
        val s = _state.value
        val today = clock.today()
        return ItemDraft.new(today).copy(
            name = s.name.trim(),
            category = s.category,
            barcode = s.barcode,
            brand = s.product?.brand,
            expiresOn = s.selectedCandidate?.date ?: today.plus(s.category.defaultShelfLifeDays, DateTimeUnit.DAY),
        )
    }

    fun continueToForm() {
        if (!_state.value.canContinue) return
        onContinue(makeDraft())
    }
}
