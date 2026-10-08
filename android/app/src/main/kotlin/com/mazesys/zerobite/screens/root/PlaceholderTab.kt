package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder for the Recipes and Settings tabs until Plan 3. */
@Composable
fun PlaceholderTab(overline: String, title: String) {
    Column(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary).padding(horizontal = Spacing.gutter)) {
        ScreenHeader(title = title, overline = overline)
        Text(
            "Arrives in the next build.",
            style = ZeroBiteTheme.type.body,
            color = ZeroBiteTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg),
        )
    }
}
