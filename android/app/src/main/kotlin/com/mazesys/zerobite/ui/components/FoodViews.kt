package com.mazesys.zerobite.ui.components

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.quantityAndStorage
import com.mazesys.zerobite.ui.text.quantityLabel
import com.mazesys.zerobite.ui.text.shortExpiryText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Instant

/** Fraction of the shelf life already used: from when it was added to the end of its expiry day. */
fun FoodItem.shelfLifeProgress(now: Instant, timeZone: TimeZone): Float {
    val end = expiresOn.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
    val total = end - addedAt
    if (total <= Duration.ZERO) return 1f
    return ((now - addedAt) / total).toFloat()
}

/** Compact tag showing how long an item has left, tinted by urgency. */
@Composable
fun ExpiryBadge(item: FoodItem, today: LocalDate, soonThresholdDays: Int, modifier: Modifier = Modifier) {
    val tint = item.status(today, soonThresholdDays).tint(ZeroBiteTheme.colors)
    Text(
        item.shortExpiryText(today),
        modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.control))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = ZeroBiteTheme.type.caption.semibold(),
        color = tint,
    )
}

/** Two-up grid card for the Fridge screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodItemCard(
    item: FoodItem,
    today: LocalDate,
    soonThresholdDays: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecallFlagged: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = item.status(today, soonThresholdDays)
    val tint = status.tint(colors)
    AppCard(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        accent = when {
            isRecallFlagged -> colors.statusExpired
            status.needsAttention -> tint
            else -> null
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(item.category.icon, tint = tint)
            Spacer(Modifier.weight(1f))
            if (isRecallFlagged) RecallFlag()
        }
        Text(
            item.name,
            style = type.subheadline.semibold(),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.quantityLabel(), style = type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Text(item.shortExpiryText(today), style = type.caption.semibold(), color = tint, maxLines = 1)
        }
    }
}

@Composable
private fun RecallFlag() {
    Row(
        Modifier.background(ZeroBiteTheme.colors.statusExpired, RoundedCornerShape(Radius.control)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        Text(stringResource(R.string.recall_flag), style = ZeroBiteTheme.type.caption2.semibold(), color = Color.White)
    }
}

/** Full-width row, where a list reads better than a grid. */
@Composable
fun FoodItemRow(item: FoodItem, today: LocalDate, soonThresholdDays: Int, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val tint = item.status(today, soonThresholdDays).tint(colors)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(item.category.icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text(item.quantityAndStorage(), style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }
        ExpiryBadge(item, today, soonThresholdDays)
    }
}

/** Hero card for the single most urgent item, with quick actions. */
@Composable
fun UseFirstCard(
    item: FoodItem,
    today: LocalDate,
    progress: Float,
    soonThresholdDays: Int,
    othersNeedingAttention: Int,
    onOpen: () -> Unit,
    onUsed: () -> Unit,
    onFreeze: (() -> Unit)?,
    onRecipes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = item.status(today, soonThresholdDays)
    val tint = status.tint(colors)
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline(stringResource(R.string.use_first), Modifier.weight(1f))
            if (othersNeedingAttention > 0) {
                Text(pluralStringResource(R.plurals.use_first_others, othersNeedingAttention, othersNeedingAttention), style = type.caption, color = colors.textSecondary)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = type.title3.semibold(), color = colors.textPrimary)
                Text(item.quantityAndStorage(), style = type.caption, color = colors.textSecondary)
            }
            Countdown(item.daysUntilExpiry(today), tint)
        }
        ShelfLifeBar(progress, tint)
        Row(Modifier.fillMaxWidth().padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton(stringResource(R.string.action_used_it), onUsed, Modifier.weight(1f), kind = ButtonKind.INK, compact = true)
            if (onFreeze != null) AppButton(stringResource(R.string.action_freeze), onFreeze, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
            AppButton(stringResource(R.string.action_recipes), onRecipes, Modifier.weight(1f), kind = ButtonKind.TONAL, compact = true)
        }
    }
}

@Composable
private fun Countdown(days: Int, tint: Color) {
    val big = ZeroBiteTheme.type.largeTitle.semibold().copy(fontSize = 40.sp, lineHeight = 44.sp)
    val small = ZeroBiteTheme.type.subheadline
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        when {
            days < 0 -> {
                Text("${-days}", style = big, letterSpacing = (-1.5).sp, color = tint, modifier = Modifier.alignByBaseline())
                Text(pluralStringResource(R.plurals.countdown_days_over, -days), style = small, color = tint, modifier = Modifier.alignByBaseline())
            }
            days == 0 -> Text(stringResource(R.string.countdown_today), style = ZeroBiteTheme.type.title.semibold(), letterSpacing = (-1).sp, color = tint)
            else -> {
                Text("$days", style = big, letterSpacing = (-1.5).sp, color = tint, modifier = Modifier.alignByBaseline())
                Text(pluralStringResource(R.plurals.countdown_days, days), style = small, color = tint, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

