package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primaryTitle: String? = null,
    primaryIcon: ImageVector? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryTitle: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = ZeroBiteTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IconTile(icon, tint = colors.brandOrange, size = 56.dp)
        Text(
            title,
            style = ZeroBiteTheme.type.title2.semibold(),
            letterSpacing = (-0.5).sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(message, style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        if (primaryTitle != null && onPrimary != null) {
            AppButton(primaryTitle, onPrimary, Modifier.padding(top = Spacing.md), kind = ButtonKind.BRAND, icon = primaryIcon)
        }
        if (secondaryTitle != null && onSecondary != null) {
            AppButton(secondaryTitle, onSecondary, kind = ButtonKind.TONAL)
        }
    }
}
