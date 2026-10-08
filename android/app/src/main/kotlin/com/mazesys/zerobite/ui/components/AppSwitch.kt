package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** Label with a switch tinted burnt orange (iOS Toggle with `.tint(BrandOrange)`). */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = label,
) {
    val colors = ZeroBiteTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.brandOrange,
                checkedThumbColor = Color.White,
                checkedBorderColor = colors.brandOrange,
                uncheckedTrackColor = colors.chipFill,
                uncheckedThumbColor = colors.textMuted,
                uncheckedBorderColor = colors.hairline,
            ),
        )
    }
}
