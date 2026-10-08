package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

sealed interface TopBarLeading {
    /** Pops the current screen. */
    data class Back(val onClick: () -> Unit) : TopBarLeading
    /** Closes a sheet or flow. */
    data class Close(val onClick: () -> Unit) : TopBarLeading
    data object None : TopBarLeading
}

/** Replaces a system app bar on pushed screens and sheets with square header buttons. */
@Composable
fun TopBar(leading: TopBarLeading, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .background(ZeroBiteTheme.colors.backgroundPrimary)
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when (leading) {
            is TopBarLeading.Back -> HeaderIconButton(Icons.Rounded.ChevronLeft, "Back", leading.onClick, Modifier.testTag("topBar.back"))
            is TopBarLeading.Close -> HeaderIconButton(Icons.Rounded.Close, "Close", leading.onClick, Modifier.testTag("topBar.close"), bordered = false)
            TopBarLeading.None -> Unit
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
