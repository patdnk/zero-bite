package com.mazesys.zerobite.screens.welcome

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.brand.BrandMarkColors
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.IconTile
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.ShelfLifeBar
import com.mazesys.zerobite.ui.components.Tag
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun Illustration(page: WelcomePage) {
    Box(Modifier.clearAndSetSemantics {}) {
        when (page) {
            WelcomePage.WELCOME -> WelcomeArt()
            WelcomePage.SCAN -> ScanArt()
            WelcomePage.REMIND -> RemindArt()
            WelcomePage.COOK -> CookArt()
            WelcomePage.REMINDERS -> RemindersArt()
        }
    }
}

@Composable
private fun MockTile(icon: ImageVector, name: String, detail: String, label: String, tint: Color, modifier: Modifier, accent: Boolean = true) {
    val type = ZeroBiteTheme.type
    AppCard(modifier, accent = if (accent) tint else null) {
        IconTile(icon, tint = tint)
        Text(name, style = type.subheadline.semibold(), color = ZeroBiteTheme.colors.textPrimary, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 2.dp)) {
            Text(detail, style = type.caption, color = ZeroBiteTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(label, style = type.caption.semibold(), color = tint)
        }
    }
}

@Composable
private fun WelcomeArt() {
    val c = ZeroBiteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.WaterDrop, stringResource(R.string.welcome_art_yogurt), "500 g", stringResource(R.string.short_today), c.statusToday, Modifier.weight(1f))
            MockTile(Icons.Rounded.Eco, stringResource(R.string.welcome_art_spinach), stringResource(R.string.welcome_art_one_bag), stringResource(R.string.short_days, 2), c.statusSoon, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.SetMeal, stringResource(R.string.welcome_art_salmon), stringResource(R.string.welcome_art_two_pcs), stringResource(R.string.welcome_art_date_nov), c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.RiceBowl, stringResource(R.string.welcome_art_spaghetti), "500 g", stringResource(R.string.welcome_art_date_jun), c.statusFresh, Modifier.weight(1f), accent = false)
        }
        Row(Modifier.alpha(0.5f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.BakeryDining, stringResource(R.string.welcome_art_sourdough), stringResource(R.string.welcome_art_one_loaf), stringResource(R.string.short_days, 4), c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.Spa, stringResource(R.string.welcome_art_lemons), stringResource(R.string.welcome_art_three_pcs), stringResource(R.string.short_days, 9), c.statusFresh, Modifier.weight(1f), accent = false)
        }
    }
}

@Composable
private fun ScanArt() {
    val c = ZeroBiteTheme.colors
    Box(contentAlignment = Alignment.Center) {
        AppCard(Modifier.width(250.dp), padding = Spacing.xl) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.welcome_art_label_product), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textSecondary)
                Text(stringResource(R.string.welcome_art_label_size), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.textMuted)
                Text(
                    stringResource(R.string.welcome_art_label_date),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = c.textPrimary,
                    modifier = Modifier.background(c.brandOrange.copy(alpha = 0.15f)).border(2.dp, c.brandOrange).padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Icon(Icons.Rounded.QrCode, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(54.dp))
            }
        }
        Canvas(Modifier.width(300.dp).height(260.dp)) {
            val length = 28.dp.toPx()
            val stroke = 4.dp.toPx()
            for ((corner, dx, dy) in listOf(
                Triple(Offset(0f, 0f), 1f, 1f),
                Triple(Offset(size.width, 0f), -1f, 1f),
                Triple(Offset(0f, size.height), 1f, -1f),
                Triple(Offset(size.width, size.height), -1f, -1f),
            )) {
                drawLine(c.brandOrange, corner, corner + Offset(0f, dy * length), stroke, StrokeCap.Square)
                drawLine(c.brandOrange, corner, corner + Offset(dx * length, 0f), stroke, StrokeCap.Square)
            }
        }
    }
}

/** The app icon in miniature, for mock notifications. */
@Composable
private fun AppIconTile(size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.225f)).background(BrandMarkColors.ink).padding(size * 0.08f),
    ) { BrandMark() }
}

@Composable
private fun RemindArt() {
    val c = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(stringResource(R.string.use_first), Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.use_first_others, 2, 2), style = type.caption, color = c.textSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.welcome_art_spinach), style = type.title3.semibold(), color = c.textPrimary)
                    Text(stringResource(R.string.welcome_art_spinach_detail), style = type.caption, color = c.textSecondary)
                }
                Text("1", style = type.largeTitle.semibold().copy(fontSize = 40.sp), color = c.statusToday, modifier = Modifier.alignByBaseline())
                Text(" " + pluralStringResource(R.plurals.countdown_days, 1), style = type.subheadline, color = c.statusToday, modifier = Modifier.alignByBaseline())
            }
            ShelfLifeBar(0.86f, c.statusToday)
            Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockButton(stringResource(R.string.action_used_it), c.textPrimary, c.onInk, Modifier.weight(1f))
                MockButton(stringResource(R.string.action_freeze), c.chipFill, c.textPrimary, Modifier.weight(1f))
            }
        }
        AppCard(Modifier.padding(horizontal = Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppIconTile()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(stringResource(R.string.app_name), style = type.caption.semibold(), color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.welcome_art_now), style = type.caption2, color = c.textSecondary)
                    }
                    Text(stringResource(R.string.welcome_art_notification_title), style = type.subheadline.semibold(), color = c.textPrimary)
                    Text(stringResource(R.string.welcome_art_notification_body), style = type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun MockButton(text: String, background: Color, foreground: Color, modifier: Modifier) {
    Text(
        text,
        style = ZeroBiteTheme.type.subheadline.semibold(),
        color = foreground,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.background(background, RoundedCornerShape(Radius.control)).padding(vertical = 8.dp),
    )
}

@Composable
private fun CookArt() {
    val c = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    AppCard(padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(c.brandOrange.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Egg, contentDescription = null, tint = c.brandOrange, modifier = Modifier.size(48.dp))
        }
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(stringResource(R.string.welcome_art_recipe), style = type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.recipe_match_percent, 92), style = type.headline, color = c.brandOrange)
            }
            Text(stringResource(R.string.welcome_art_recipe_meta), style = type.caption, color = c.textSecondary)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(stringResource(R.string.welcome_art_spinach), tint = c.brandOrange)
                Tag(stringResource(R.string.welcome_art_eggs), tint = c.brandOrange)
                Tag("+3")
            }
        }
    }
}

@Composable
private fun RemindersArt() {
    val c = ZeroBiteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ReminderRow(stringResource(R.string.welcome_art_when_before), stringResource(R.string.welcome_art_text_before), c.statusSoon)
        ReminderRow(stringResource(R.string.welcome_art_when_day), stringResource(R.string.welcome_art_text_day), c.statusToday)
        ReminderRow(stringResource(R.string.welcome_art_when_after), stringResource(R.string.welcome_art_text_after), c.statusExpired)
    }
}

@Composable
private fun ReminderRow(whenText: String, text: String, tint: Color) {
    AppCard(accent = tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(Icons.Rounded.Notifications, tint = tint, size = 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Overline(whenText, tint = tint)
                Text(text, style = ZeroBiteTheme.type.subheadline.medium(), color = ZeroBiteTheme.colors.textPrimary)
            }
        }
    }
}
