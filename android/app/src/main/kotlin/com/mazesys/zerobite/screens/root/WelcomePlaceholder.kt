package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder until Plan 3 ports the iOS onboarding (intro pages + reminder opt-in). */
@Composable
fun WelcomePlaceholder(onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ZeroBiteTheme.colors.backgroundPrimary)
            .systemBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(Modifier.size(72.dp))
        DetailTitle(title = "Zero Bite", overline = "Welcome")
        Text(
            "Keep track of what's in your fridge, get a nudge before food goes off, and cook with what you already have.",
            style = ZeroBiteTheme.type.body,
            color = ZeroBiteTheme.colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        AppButton("Get started", onDone, Modifier.testTag("welcome.start"))
    }
}
