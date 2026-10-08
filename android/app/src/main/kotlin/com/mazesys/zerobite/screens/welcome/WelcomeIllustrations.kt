package com.mazesys.zerobite.screens.welcome

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
            MockTile(Icons.Rounded.WaterDrop, "Greek yogurt", "500 g", "Today", c.statusToday, Modifier.weight(1f))
            MockTile(Icons.Rounded.Eco, "Spinach", "1 bag", "2d", c.statusSoon, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.SetMeal, "Salmon", "2 pcs", "3 Nov", c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.RiceBowl, "Spaghetti", "500 g", "Jun", c.statusFresh, Modifier.weight(1f), accent = false)
        }
        Row(Modifier.alpha(0.5f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MockTile(Icons.Rounded.BakeryDining, "Sourdough", "1 loaf", "4d", c.statusFresh, Modifier.weight(1f), accent = false)
            MockTile(Icons.Rounded.Spa, "Lemons", "3 pcs", "9d", c.statusFresh, Modifier.weight(1f), accent = false)
        }
    }
}

@Composable
private fun ScanArt() {
    val c = ZeroBiteTheme.colors
    Box(contentAlignment = Alignment.Center) {
        AppCard(Modifier.width(250.dp), padding = Spacing.xl) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SEMI SKIMMED MILK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textSecondary)
                Text("2 PINTS · 1.136 L", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = c.textMuted)
                Text(
                    "USE BY 12 OCT",
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
                Overline("Use first", Modifier.weight(1f))
                Text("2 more need attention", style = type.caption, color = c.textSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Spinach", style = type.title3.semibold(), color = c.textPrimary)
                    Text("1 bag · Fridge", style = type.caption, color = c.textSecondary)
                }
                Text("1", style = type.largeTitle.semibold().copy(fontSize = 40.sp), color = c.statusToday, modifier = Modifier.alignByBaseline())
                Text(" day", style = type.subheadline, color = c.statusToday, modifier = Modifier.alignByBaseline())
            }
            ShelfLifeBar(0.86f, c.statusToday)
            Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MockButton("Used it", c.textPrimary, c.onInk, Modifier.weight(1f))
                MockButton("Freeze", c.chipFill, c.textPrimary, Modifier.weight(1f))
            }
        }
        AppCard(Modifier.padding(horizontal = Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AppIconTile()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text("Zero Bite", style = type.caption.semibold(), color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text("now", style = type.caption2, color = c.textSecondary)
                    }
                    Text("Spinach goes off tomorrow", style = type.subheadline.semibold(), color = c.textPrimary)
                    Text("It's in 3 recipes you can make tonight.", style = type.caption, color = c.textSecondary)
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
                Text("Spinach and egg frittata", style = type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text("92%", style = type.headline, color = c.brandOrange)
            }
            Text("20 min · serves 2 · uses 2 expiring", style = type.caption, color = c.textSecondary)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("Spinach", tint = c.brandOrange)
                Tag("Eggs", tint = c.brandOrange)
                Tag("+3")
            }
        }
    }
}

@Composable
private fun RemindersArt() {
    val c = ZeroBiteTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ReminderRow("3 days before", "Greek yogurt is due on Friday", c.statusSoon)
        ReminderRow("On the day", "Use your spinach today", c.statusToday)
        ReminderRow("Day after", "Milk went past its date yesterday", c.statusExpired)
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
