package com.mazesys.zerobite.ui.components

import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.resolve
import com.mazesys.zerobite.ui.text.shortExpiryText
import com.mazesys.zerobite.ui.text.uiText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.model.Recipe
import com.mazesys.zerobite.core.recipes.RecipeMatch
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteColors
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Placeholder artwork until recipes carry photos. From the title only: tags say nothing about the look. */
val Recipe.icon: ImageVector
    get() {
        val haystack = title.lowercase()
        val rules: List<Pair<ImageVector, List<String>>> = listOf(
            Icons.Rounded.LocalCafe to listOf("smoothie", "parfait", "oats", "porridge", "granola", "shake"),
            Icons.Rounded.SoupKitchen to listOf("soup", "broth", "stew", "chowder"),
            Icons.Rounded.Eco to listOf("salad", "slaw"),
            Icons.Rounded.SetMeal to listOf("fish", "salmon", "tuna", "prawn", "cod"),
            Icons.Rounded.Cake to listOf("cake", "bake", "muffin", "pudding", "dessert", "crumble", "bread"),
            Icons.Rounded.Egg to listOf("omelette", "frittata", "fried", "fry", "scrambled", "egg", "pancake", "hash"),
            Icons.Rounded.LunchDining to listOf("wrap", "sandwich", "toastie", "burger", "quesadilla"),
            Icons.Rounded.DinnerDining to listOf("pasta", "spaghetti", "carbonara", "risotto", "rice", "noodle"),
            Icons.Rounded.Grass to listOf("vegetable", "curry", "roast", "potato", "carrot"),
        )
        return rules.firstOrNull { (_, needles) -> needles.any { haystack.contains(it) } }?.first ?: Icons.Rounded.Restaurant
    }

/** Share of required ingredients already in the fridge, 0–100. */
val RecipeMatch.matchPercent: Int
    get() = if (requiredCount == 0) 100 else (requiredMatchedCount.toDouble() / requiredCount * 100).roundToInt()

/** "Ready to cook", "Missing milk", "Missing a, b +2". */
val RecipeMatch.statusText: UiText
    get() {
        if (canMake) return uiText(R.string.recipe_ready_to_cook)
        val names = missing.take(2).joinToString(", ") { it.name }
        return if (missing.size > 2) uiText(R.string.recipe_missing_more, names, missing.size - 2) else uiText(R.string.recipe_missing, names)
    }

fun RecipeMatch.accent(colors: ZeroBiteColors): Color = when {
    expiringItems.isNotEmpty() -> colors.brandOrange
    canMake -> colors.statusFresh
    else -> colors.brandYellow
}

/** Tinted panel standing in for a recipe photo. */
@Composable
fun RecipeArtwork(recipe: Recipe, tint: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height).background(tint.copy(alpha = 0.14f)).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(recipe.icon, contentDescription = null, tint = tint, modifier = Modifier.size(height * 0.4f))
    }
}

/** Large card for the best match at the top of the Recipes screen. */
@Composable
fun FeaturedRecipeCard(match: RecipeMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val accent = match.accent(colors)
    val recipe = match.recipe
    val percentDescription = stringResource(R.string.recipe_match_percent_description, match.matchPercent)
    val meta = buildList {
        add(stringResource(R.string.recipe_minutes, recipe.prepMinutes))
        add(stringResource(R.string.recipe_serves, recipe.servings))
        if (match.expiringItems.isNotEmpty()) add(stringResource(R.string.recipe_uses_expiring, match.expiringItems.size))
    }.joinToString(" · ")

    AppCard(modifier.clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClick = onClick), padding = 0.dp) {
        RecipeArtwork(recipe, accent, 110.dp)
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(recipe.title, style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.recipe_match_percent, match.matchPercent),
                    style = type.headline,
                    color = accent,
                    modifier = Modifier.padding(start = Spacing.sm).semantics {
                        contentDescription = percentDescription
                    },
                )
            }
            Text(meta, style = type.caption, color = colors.textSecondary)
            if (match.expiringItems.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.expiringItems.take(3).forEach { Tag(it.name, tint = colors.brandOrange) }
                    if (match.expiringItems.size > 3) Tag("+${match.expiringItems.size - 3}")
                }
            } else {
                Text(
                    match.statusText.resolve(),
                    style = type.caption.semibold(),
                    color = if (match.canMake) colors.statusFresh else colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Half-width grid card. */
@Composable
fun RecipeCard(match: RecipeMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    AppCard(modifier.clip(RoundedCornerShape(Radius.card)).clickable(role = Role.Button, onClick = onClick), padding = 10.dp) {
        RecipeArtwork(match.recipe, match.accent(colors), 60.dp, Modifier.clip(RoundedCornerShape(Radius.control)))
        Text(
            match.recipe.title,
            style = type.subheadline.semibold(),
            color = colors.textPrimary,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            match.statusText.resolve(),
            style = type.caption,
            color = if (match.canMake) colors.statusFresh else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            stringResource(R.string.recipe_card_meta, match.recipe.prepMinutes, match.matchPercent),
            style = type.caption2,
            color = colors.textMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Full-width row for lists inside cards ("Recipes using …" on item detail). */
@Composable
fun RecipeRow(match: RecipeMatch, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(match.recipe.icon, tint = match.accent(colors), size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(match.recipe.title, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text(
                stringResource(R.string.recipe_row_meta, match.recipe.prepMinutes, match.statusText.resolve()),
                style = ZeroBiteTheme.type.caption,
                color = if (match.canMake) colors.statusFresh else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
    }
}

/** Tappable chip for an expiring item, used in the "Use these first" strip. */
@Composable
fun ExpiringChip(item: FoodItem, today: LocalDate, soonThresholdDays: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val tint = item.status(today, soonThresholdDays).tint(colors)
    val shape = RoundedCornerShape(Radius.control)
    Row(
        modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, tint.copy(alpha = 0.4f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(item.category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(item.name, style = ZeroBiteTheme.type.caption, color = colors.textPrimary, maxLines = 1)
        Text(item.shortExpiryText(today), style = ZeroBiteTheme.type.caption.semibold(), color = tint)
    }
}
