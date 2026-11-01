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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

enum class RecipeScope(@StringRes val title: Int, @StringRes val sectionTitle: Int) {
    READY(R.string.recipes_scope_ready, R.string.recipes_section_ready),
    NEARLY(R.string.recipes_scope_nearly, R.string.recipes_section_nearly),
    ALL(R.string.recipes_scope_all, R.string.recipes_section_all),
}

/**
 * The catalogue ranked against the fridge. Producing this is the expensive step, so it stays apart
 * from the scope and search filtering and only runs when the fridge, settings, catalogue or date change.
 */
class MatchedRecipes(
    val today: LocalDate,
    val soonThresholdDays: Int,
    val aliases: IngredientAliases,
    /** Items needing attention, for the "Use these first" strip. */
    val expiringItems: List<FoodItem>,
    val matches: List<RecipeMatch>,
) {
    companion object {
        fun of(
            active: List<FoodItem>,
            recipes: List<Recipe>,
            aliases: IngredientAliases,
            soonThresholdDays: Int,
            today: LocalDate,
        ) = MatchedRecipes(
            today = today,
            soonThresholdDays = soonThresholdDays,
            aliases = aliases,
            expiringItems = active.filter { it.status(today, soonThresholdDays).needsAttention },
            matches = RecipeMatcher(today, soonThresholdDays, aliases).match(recipes, active),
        )
    }
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
        fun build(matched: MatchedRecipes, scope: RecipeScope, searchText: String): RecipesUiState {
            val query = searchText.trim().lowercase()
            val displayed = matched.matches.filter { match ->
                val inScope = when (scope) {
                    RecipeScope.READY -> match.canMake
                    RecipeScope.NEARLY -> match.missing.size in 1..2
                    RecipeScope.ALL -> true
                }
                inScope && (query.isEmpty() || RecipeMatcher.recipeMatchesQuery(match.recipe, query, matched.aliases))
            }
            return RecipesUiState(
                today = matched.today,
                isLoading = false,
                scope = scope,
                searchText = searchText,
                soonThresholdDays = matched.soonThresholdDays,
                expiringItems = matched.expiringItems,
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
    computation: CoroutineDispatcher,
) : ViewModel() {

    private val scope = MutableStateFlow(RecipeScope.READY)
    private val searchText = MutableStateFlow("")
    private val today = MutableStateFlow(clock.today())

    private val matched: Flow<MatchedRecipes> =
        combine(inventory.items, settings.state, recipes.catalogue, today) { items, s, all, date ->
            MatchedRecipes.of(items.filterNot { it.isConsumed }, all, recipes.ingredientAliases(), s.soonThresholdDays, date)
        }.flowOn(computation)

    /** Stays on the main thread, without waiting for [matched]: the search field shows this state's text. */
    val state: StateFlow<RecipesUiState> =
        combine(matched.onStart<MatchedRecipes?> { emit(null) }, scope, searchText) { m, sc, query ->
            if (m == null) RecipesUiState(today = clock.today(), scope = sc, searchText = query) else RecipesUiState.build(m, sc, query)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipesUiState(today = clock.today()))

    fun setScope(value: RecipeScope) {
        scope.value = value
        today.value = clock.today()
    }

    fun setSearchText(text: String) {
        searchText.value = text
        today.value = clock.today()
    }

    fun showRecipe(match: RecipeMatch) = coordinator.showRecipe(match.recipe.id)

    fun showItem(item: FoodItem) = coordinator.showItem(item.id)
}
