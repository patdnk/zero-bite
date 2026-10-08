package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Material dropdown restyled: card surface, hairline border, tight corners. */
@Composable
fun AppDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = ZeroBiteTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.card),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.hairline),
        content = content,
    )
}

@Composable
fun AppMenuItem(title: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    val colors = ZeroBiteTheme.colors
    val tint = if (destructive) colors.statusExpired else colors.textPrimary
    DropdownMenuItem(
        text = { Text(title, style = ZeroBiteTheme.type.subheadline, color = tint) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}
