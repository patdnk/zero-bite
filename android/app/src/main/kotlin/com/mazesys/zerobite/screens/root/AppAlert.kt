package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.resolve
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

data class AlertButton(val title: UiText, val isDestructive: Boolean = false, val action: () -> Unit = {})

data class AlertConfig(
    val title: UiText,
    val message: UiText? = null,
    val primary: AlertButton,
    val secondary: AlertButton? = null,
)

/** The app's alert dialog: a title, an optional message and one or two buttons. */
@Composable
fun AppAlert(config: AlertConfig, onDismiss: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(config.title.resolve(), style = ZeroBiteTheme.type.headline) },
        text = config.message?.let { message -> { Text(message.resolve(), style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary) } },
        confirmButton = {
            TextButton(onClick = { config.primary.action(); onDismiss() }) {
                Text(
                    config.primary.title.resolve(),
                    style = ZeroBiteTheme.type.subheadline.semibold(),
                    color = if (config.primary.isDestructive) colors.statusExpired else colors.textPrimary,
                )
            }
        },
        dismissButton = config.secondary?.let { secondary ->
            {
                TextButton(onClick = { secondary.action(); onDismiss() }) {
                    Text(secondary.title.resolve(), style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary)
                }
            }
        },
        containerColor = colors.backgroundPrimary,
        shape = RoundedCornerShape(Radius.card),
    )
}
