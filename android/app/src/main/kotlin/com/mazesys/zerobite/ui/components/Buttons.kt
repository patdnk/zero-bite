package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

enum class ButtonKind {
    /** Dark ink fill: the main action on a screen. */
    INK,
    /** Burnt orange fill: scanning and other "do it now" actions. */
    BRAND,
    /** Light chip fill: secondary actions next to a primary one. */
    TONAL,
    /** Text only. */
    PLAIN,
}

/** Flat, tight-cornered, full-width button shared by every action in the app. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.INK,
    compact: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (background, foreground) = when (kind) {
        ButtonKind.INK -> colors.textPrimary to colors.onInk
        ButtonKind.BRAND -> colors.brandOrange to Color.White
        ButtonKind.TONAL -> colors.chipFill to colors.textPrimary
        ButtonKind.PLAIN -> Color.Transparent to colors.textSecondary
    }
    val active = enabled && !loading
    val style = (if (compact) ZeroBiteTheme.type.subheadline else ZeroBiteTheme.type.headline).semibold()

    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (!active) 0.45f else if (pressed) 0.75f else 1f)
            .clip(RoundedCornerShape(Radius.control))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = style, color = foreground)
        }
    }
}
