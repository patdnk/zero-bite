package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.shortExpiryText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Countertops
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.MatchedIngredient
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.RecipeArtwork
import com.mazesys.zerobite.ui.components.Tag
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.accent
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.components.matchPercent
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.LocalDate

@Composable
fun RecipeDetailScreen(viewModel: RecipeDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        val recipe = ui.recipe
        val match = ui.match
        when {
            ui.isLoading -> Unit
            recipe == null || match == null -> EmptyStateView(
                title = stringResource(R.string.recipe_not_found_title),
                message = stringResource(R.string.recipe_not_found_message),
                icon = Icons.Rounded.QuestionMark,
                modifier = Modifier.padding(Spacing.gutter),
            )
            else -> RecipeContent(recipe, match, ui, viewModel)
        }
    }
}

@Composable
private fun RecipeContent(recipe: Recipe, match: RecipeMatch, ui: RecipeDetailUiState, viewModel: RecipeDetailViewModel) {
    val colors = ZeroBiteTheme.colors
    val accent = match.accent(colors)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        RecipeArtwork(recipe, accent, 150.dp, Modifier.clip(RoundedCornerShape(Radius.card)))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DetailTitle(title = recipe.title, overline = stringResource(R.string.recipe_percent_in_fridge, match.matchPercent), overlineTint = accent)
            Text(recipe.summary, style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Stat("${recipe.prepMinutes}", stringResource(R.string.recipe_stat_minutes), Icons.Rounded.Schedule, Modifier.weight(1f))
            Stat("${recipe.servings}", pluralStringResource(R.plurals.recipe_stat_servings, recipe.servings), Icons.Rounded.People, Modifier.weight(1f))
            Stat(
                "${match.expiringItems.size}", stringResource(R.string.recipe_stat_expiring), Icons.Rounded.Schedule, Modifier.weight(1f),
                tint = if (match.expiringItems.isEmpty()) null else colors.brandOrange,
            )
        }
        StatusBanner(match)
        if (recipe.tags.isNotEmpty()) {
            LazyRow(
                Modifier.bleed(Spacing.gutter),
                contentPadding = PaddingValues(horizontal = Spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(recipe.tagLabels) { Tag(it) }
            }
        }
        CardSection(
            title = stringResource(R.string.recipe_ingredients),
            rows = recipe.ingredients.map { ingredient ->
                { IngredientRow(ingredient, ui.matched(ingredient), ui.today, ui.soonThresholdDays, onTapItem = viewModel::showItem) }
            },
        )
        CardSection(
            title = stringResource(R.string.recipe_method),
            rows = recipe.steps.mapIndexed { index, step ->
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text(
                            (index + 1).toString().padStart(2, '0'),
                            style = ZeroBiteTheme.type.subheadline.semibold(),
                            color = colors.brandOrange,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(step, style = ZeroBiteTheme.type.subheadline, color = colors.textPrimary)
                    }
                }
            },
        )
        AppButton(
            stringResource(R.string.recipe_cooked_button),
            viewModel::cooked,
            Modifier.testTag("recipe.cooked"),
            icon = Icons.Rounded.Verified,
            enabled = ui.canMarkCooked,
        )
    }
}

@Composable
private fun Stat(value: String, unit: String, icon: ImageVector, modifier: Modifier, tint: Color? = null) {
    val colors = ZeroBiteTheme.colors
    AppCard(modifier, padding = 10.dp) {
        Icon(icon, contentDescription = null, tint = tint ?: colors.textSecondary, modifier = Modifier.size(15.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = ZeroBiteTheme.type.title3.semibold(), color = tint ?: colors.textPrimary, modifier = Modifier.alignByBaseline())
            Text(unit, style = ZeroBiteTheme.type.caption, color = colors.textSecondary, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun StatusBanner(match: RecipeMatch) {
    val colors = ZeroBiteTheme.colors
    val tint = if (match.canMake) colors.statusFresh else colors.brandYellow
    Row(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.card)).padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(if (match.canMake) Icons.Rounded.CheckCircle else Icons.Rounded.ShoppingCart, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            if (match.canMake) stringResource(R.string.recipe_have_everything) else stringResource(R.string.recipe_missing_sentence, match.missing.joinToString(", ") { it.missingLabel }),
            style = ZeroBiteTheme.type.subheadline.medium(),
            color = tint,
        )
    }
}

@Composable
private fun IngredientRow(
    ingredient: Recipe.Ingredient,
    matched: MatchedIngredient?,
    today: LocalDate,
    soonThresholdDays: Int,
    onTapItem: (FoodItem) -> Unit,
) {
    val colors = ZeroBiteTheme.colors
    val item = matched?.item
    val status = item?.status(today, soonThresholdDays)
    val icon = when {
        status != null -> if (status.needsAttention) Icons.Rounded.Error else Icons.Rounded.CheckCircle
        matched?.isStaple == true -> Icons.Rounded.Countertops
        else -> Icons.Rounded.RadioButtonUnchecked
    }
    val tint = when {
        status != null -> if (status.needsAttention) status.tint(colors) else colors.statusFresh
        else -> colors.textSecondary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (ingredient.amount.isEmpty()) ingredient.label else "${ingredient.amount} ${ingredient.label}",
                style = ZeroBiteTheme.type.subheadline.medium(),
                color = colors.textPrimary,
            )
            val caption = ZeroBiteTheme.type.caption
            when {
                item != null -> Text(
                    stringResource(R.string.recipe_you_have, item.name, item.shortExpiryText(today)),
                    style = caption,
                    color = tint,
                    modifier = Modifier.clickable(role = Role.Button) { onTapItem(item) },
                )
                matched?.isStaple == true -> Text(stringResource(R.string.recipe_store_cupboard), style = caption, color = colors.textSecondary)
                ingredient.optional -> Text(stringResource(R.string.recipe_optional), style = caption, color = colors.textSecondary)
                else -> Text(stringResource(R.string.recipe_not_in_fridge), style = caption, color = colors.textSecondary)
            }
        }
    }
}
