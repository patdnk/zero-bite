package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Card with a hairline border. `accent` draws a 3dp status edge down the leading side. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .drawWithContent {
                drawContent()
                if (accent != null) drawRect(accent, size = Size(3.dp.toPx(), size.height))
            }
            .padding(padding),
        content = content,
    )
}

/** Tinted square tile holding an icon (category icons, empty states). */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
    size: Dp = 32.dp,
) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.12f), RoundedCornerShape(Radius.control)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Square header button (add, back, more). Bordered on pages; borderless and tonal for closing sheets. */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.control)
    Box(
        modifier
            .size(38.dp)
            .clip(shape)
            .background(if (bordered) colors.surface else colors.chipFill)
            .then(if (bordered) Modifier.border(1.dp, colors.hairline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}

/** Thin progress bar with square ends. `progress`: 0 = just bought, 1 = at or past the date. */
@Composable
fun ShelfLifeBar(progress: Float, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(ZeroBiteTheme.colors.chipFill)
            .clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(tint))
    }
}
