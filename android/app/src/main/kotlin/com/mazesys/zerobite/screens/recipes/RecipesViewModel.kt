package com.mazesys.zerobite.screens.recipes

import androidx.annotation.StringRes
import com.mazesys.zerobite.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.coordination.RecipesCoordinator
import com.mazesys.zerobite.core.inventory.InventoryRepository
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.IngredientAliases
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.core.recipes.RecipeMatcher
import com.mazesys.zerobite.core.recipes.RecipeRepository
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

enum class RecipeScope(@StringRes val title: Int, @StringRes val sectionTitle: Int) {
    READY(R.string.recipes_scope_ready, R.string.recipes_section_ready),
    NEARLY(R.string.recipes_scope_nearly, R.string.recipes_section_nearly),
    ALL(R.string.recipes_scope_all, R.string.recipes_section_all),
}

data class RecipesUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val scope: RecipeScope = RecipeScope.READY,
    val searchText: String = "",
    val soonThresholdDays: Int = 3,
    /** Items needing attention, for the "Use these first" strip. */
    val expiringItems: List<FoodItem> = emptyList(),
    val displayed: List<RecipeMatch> = emptyList(),
) {
    val showsExpiringStrip: Boolean get() = expiringItems.isNotEmpty() && searchText.isEmpty()

    @get:StringRes
    val emptyTitle: Int
        get() = when (scope) {
            RecipeScope.READY -> R.string.recipes_empty_ready_title
            RecipeScope.NEARLY -> R.string.recipes_empty_nearly_title
            RecipeScope.ALL -> R.string.recipes_empty_all_title
        }

    @get:StringRes
    val emptyMessage: Int
        get() = when {
            searchText.isNotEmpty() -> R.string.recipes_empty_search
            scope == RecipeScope.READY -> R.string.recipes_empty_ready_message
            scope == RecipeScope.NEARLY -> R.string.recipes_empty_nearly_message
            else -> R.string.recipes_empty_all_message
        }

    companion object {
        fun build(
            active: List<FoodItem>,
            recipes: List<Recipe>,
            aliases: IngredientAliases,
            scope: RecipeScope,
            searchText: String,
            soonThresholdDays: Int,
            today: LocalDate,
        ): RecipesUiState {
            val matches = RecipeMatcher(today, soonThresholdDays, aliases).match(recipes, active)
            val query = searchText.trim().lowercase()
            val displayed = matches.filter { match ->
                val inScope = when (scope) {
                    RecipeScope.READY -> match.canMake
                    RecipeScope.NEARLY -> match.missing.size in 1..2
                    RecipeScope.ALL -> true
                }
                inScope && (query.isEmpty() || RecipeMatcher.recipeMatchesQuery(match.recipe, query, aliases))
            }
            return RecipesUiState(
                today = today,
                isLoading = false,
                scope = scope,
                searchText = searchText,
                soonThresholdDays = soonThresholdDays,
                expiringItems = active.filter { it.status(today, soonThresholdDays).needsAttention },
                displayed = displayed,
            )
        }
    }
}

/** Recipes ranked against the fridge, with a scope filter and search. */
class RecipesViewModel(
    inventory: InventoryRepository,
    settings: SettingsRepository,
    recipes: RecipeRepository,
    private val clock: AppClock,
    private val coordinator: RecipesCoordinator,
) : ViewModel() {

    private val scope = MutableStateFlow(RecipeScope.READY)
    private val searchText = MutableStateFlow("")

    val state: StateFlow<RecipesUiState> =
        combine(inventory.items, settings.state, recipes.catalogue, scope, searchText) { items, s, all, sc, query ->
            RecipesUiState.build(items.filterNot { it.isConsumed }, all, recipes.ingredientAliases(), sc, query, s.soonThresholdDays, clock.today())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipesUiState(today = clock.today()))

    fun setScope(value: RecipeScope) {
        scope.value = value
    }

    fun setSearchText(text: String) {
        searchText.value = text
    }

    fun showRecipe(match: RecipeMatch) = coordinator.showRecipe(match.recipe.id)

    fun showItem(item: FoodItem) = coordinator.showItem(item.id)
}
