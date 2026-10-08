package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Label with − / + buttons, clamped to `range` (iOS Stepper). */
@Composable
fun AppStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        StepButton(Icons.Rounded.Remove, "Decrease", enabled = value > range.first) { onChange(value - 1) }
        StepButton(Icons.Rounded.Add, "Increase", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(Radius.control))
            .background(ZeroBiteTheme.colors.chipFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ZeroBiteTheme.colors.textPrimary, modifier = Modifier.size(18.dp))
    }
}
