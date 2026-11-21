package com.mazesys.zerobite.screens.recipes

import androidx.annotation.StringRes
import com.mazesys.zerobite.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookedUiState(
    val isLoaded: Boolean = false,
    val recipeTitle: String? = null,
    val usableItems: List<FoodItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) {
    @get:StringRes val confirmTitle: Int get() = if (selectedIds.isEmpty()) R.string.action_done else R.string.cooked_update_fridge
}

/** After cooking: choose which matched fridge items were used up. */
class CookedViewModel(
    recipeId: String,
    private val inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val onDone: () -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(CookedUiState())
    val state: StateFlow<CookedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val recipe = recipes.recipes().firstOrNull { it.id == recipeId }
            val active = inventory.items.first().filterNot { it.isConsumed }
            val usable = recipe
                ?.let { RecipeMatcher(clock.today(), settings.current().soonThresholdDays, recipes.ingredientAliases()).match(it, active).usedItems }
                .orEmpty()
            _state.value = CookedUiState(true, recipe?.title, usable, usable.map { it.id }.toSet())
        }
    }

    fun toggle(item: FoodItem) {
        _state.update { s -> s.copy(selectedIds = if (item.id in s.selectedIds) s.selectedIds - item.id else s.selectedIds + item.id) }
    }

    fun confirm() {
        val selected = _state.value.selectedIds
        viewModelScope.launch {
            // Skip anything used up elsewhere since the sheet opened, so its date isn't overwritten.
            val stillActive = inventory.items.first().filterNot { it.isConsumed }.map { it.id }.toSet()
            val ids = selected.filter { it in stillActive }
            if (ids.isNotEmpty()) inventory.markConsumed(ids, clock.now())
            onDone()
        }
    }

    fun cancel() = onDone()
}
