package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.BottomSearchBar
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.ExpiringChip
import com.mazesys.zerobite.ui.components.FeaturedRecipeCard
import com.mazesys.zerobite.ui.components.RecipeCard
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SectionHeading
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

@Composable
fun RecipesScreen(viewModel: RecipesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).testTag("recipes.grid"),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            fullWidth("header") { ScreenHeader(title = stringResource(R.string.recipes_title), overline = stringResource(R.string.recipes_overline)) }
            if (state.showsExpiringStrip) {
                fullWidth("strip") {
                    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeading(title = stringResource(R.string.recipes_use_these_first), tint = ZeroBiteTheme.colors.brandOrange, icon = Icons.Rounded.Schedule)
                        LazyRow(
                            Modifier.bleed(Spacing.gutter),
                            contentPadding = PaddingValues(horizontal = Spacing.gutter),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.expiringItems, key = { it.id }) { item ->
                                ExpiringChip(item, state.today, state.soonThresholdDays, onClick = { viewModel.showItem(item) })
                            }
                        }
                    }
                }
            }
            fullWidth("scopes") {
                ChipFilter(
                    options = RecipeScope.entries,
                    selected = state.scope,
                    onSelect = viewModel::setScope,
                    label = { stringResource(it.title) },
                    modifier = Modifier.bleed(Spacing.gutter).padding(vertical = Spacing.xs),
                )
            }
            if (!state.isLoading) results(state, viewModel)
        }
        BottomSearchBar(
            text = state.searchText,
            onTextChange = viewModel::setSearchText,
            placeholder = stringResource(R.string.recipes_search_placeholder),
            testTag = "recipes.search",
        )
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.results(state: RecipesUiState, viewModel: RecipesViewModel) {
    val matches = state.displayed
    if (matches.isEmpty()) {
        fullWidth("empty") {
            AppCard(padding = Spacing.lg) {
                Text(stringResource(state.emptyTitle), style = ZeroBiteTheme.type.headline, color = ZeroBiteTheme.colors.textPrimary)
                Text(
                    stringResource(state.emptyMessage),
                    style = ZeroBiteTheme.type.subheadline,
                    color = ZeroBiteTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        return
    }
    fullWidth("section") {
        SectionHeading(
            title = stringResource(state.scope.sectionTitle),
            detail = pluralStringResource(R.plurals.recipe_count, matches.size, matches.size),
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
    fullWidth("featured-${matches.first().recipe.id}") {
        FeaturedRecipeCard(matches.first(), onClick = { viewModel.showRecipe(matches.first()) })
    }
    items(matches.drop(1), key = { it.recipe.id }) { match ->
        RecipeCard(match, onClick = { viewModel.showRecipe(match) })
    }
}
