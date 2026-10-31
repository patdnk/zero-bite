package com.mazesys.zerobite.screens.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class RecipeDetailUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val recipe: Recipe? = null,
    val match: RecipeMatch? = null,
    val soonThresholdDays: Int = 3,
) {
    /** Only useful when at least one fridge item would be consumed. */
    val canMarkCooked: Boolean get() = match?.usedItems?.isNotEmpty() == true

    fun matched(ingredient: Recipe.Ingredient): MatchedIngredient? = match?.matched?.firstOrNull { it.ingredient == ingredient }
}

class RecipeDetailViewModel(
    val recipeId: String,
    inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val onShowItem: (String) -> Unit,
    private val onCooked: (String) -> Unit,
) : ViewModel() {

    val state: StateFlow<RecipeDetailUiState> =
        combine(inventory.items, settings.state, recipes.catalogue) { items, s, all ->
            val today = clock.today()
            val recipe = all.firstOrNull { it.id == recipeId }
            RecipeDetailUiState(
                today = today,
                isLoading = false,
                recipe = recipe,
                match = recipe?.let { RecipeMatcher(today, s.soonThresholdDays, recipes.ingredientAliases()).match(it, items.filterNot { item -> item.isConsumed }) },
                soonThresholdDays = s.soonThresholdDays,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipeDetailUiState(today = clock.today()))

    fun showItem(item: FoodItem) = onShowItem(item.id)

    fun cooked() = onCooked(recipeId)
}
