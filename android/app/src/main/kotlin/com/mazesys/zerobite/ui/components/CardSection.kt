package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** A titled group of rows inside one card, divided by hairlines, with optional footer text. */
@Composable
fun CardSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    rows: List<@Composable () -> Unit>,
) {
    val colors = ZeroBiteTheme.colors
    val shape = RoundedCornerShape(Radius.card)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (title != null) Overline(title, Modifier.semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface).border(1.dp, colors.hairline, shape)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 11.dp)) { row() }
            }
        }
        if (footer != null) {
            Text(footer, style = ZeroBiteTheme.type.caption, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}

/** Label on the left, value on the right. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    InfoRow(label, modifier, icon) {
        Text(value, style = ZeroBiteTheme.type.subheadline.medium(), color = ZeroBiteTheme.colors.textPrimary)
    }
}

/** Label on the left, any control on the right. */
@Composable
fun InfoRow(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: @Composable () -> Unit) {
    val colors = ZeroBiteTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(label, style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Row-shaped button inside a CardSection. */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = ZeroBiteTheme.colors.textPrimary,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 24.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, style = ZeroBiteTheme.type.subheadline.medium(), color = tint)
    }
}
