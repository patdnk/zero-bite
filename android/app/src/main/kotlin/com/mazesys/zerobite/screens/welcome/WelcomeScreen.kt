package com.mazesys.zerobite.screens.welcome

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.BrandLockup
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val requester = LocalPermissionRequester.current
    val pager = rememberPagerState { ui.pages.size }

    // The view model owns the page; the pager follows it and reports swipes back.
    LaunchedEffect(ui.pageIndex) { if (pager.currentPage != ui.pageIndex) pager.animateScrollToPage(ui.pageIndex) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { viewModel.setPage(ui.pages[it]) } }

    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).padding(horizontal = Spacing.xl).padding(top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(ui.pages.size, ui.pageIndex)
            Spacer(Modifier.weight(1f))
            if (!ui.isLastPage) {
                Text(
                    ui.skipTitle,
                    style = ZeroBiteTheme.type.subheadline.medium(),
                    color = ZeroBiteTheme.colors.textSecondary,
                    modifier = Modifier.testTag("welcome.skip").clickable(role = Role.Button) { viewModel.skipIntro() },
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !ui.isRequesting) { index ->
            OnboardingPage(ui.pages[index], denied = ui.step == WelcomeStep.DENIED)
        }
        Column(Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val onReminders = ui.currentPage == WelcomePage.REMINDERS
            AppButton(
                ui.primaryTitle,
                { viewModel.primaryAction(requester) },
                Modifier.testTag("welcome.primary"),
                kind = if (onReminders) ButtonKind.BRAND else ButtonKind.INK,
                icon = if (onReminders && ui.step != WelcomeStep.DENIED) Icons.Rounded.NotificationsActive else null,
                loading = ui.isRequesting,
            )
            if (ui.showsNotNow) {
                AppButton("Not now", viewModel::skip, Modifier.testTag("welcome.notNow"), kind = ButtonKind.PLAIN)
            }
        }
    }
}

/** Bespoke page dots: short bars, the current one stretched and orange. */
@Composable
private fun PageIndicator(count: Int, index: Int) {
    val colors = ZeroBiteTheme.colors
    Row(
        Modifier.semantics { contentDescription = "Page ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { position ->
            val width by animateDpAsState(if (position == index) 22.dp else 8.dp, label = "dot")
            Box(Modifier.width(width).height(4.dp).background(if (position == index) colors.brandOrange else colors.chipFill))
        }
    }
}

@Composable
private fun OnboardingPage(page: WelcomePage, denied: Boolean) {
    val colors = ZeroBiteTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
        Spacer(Modifier.weight(1f))
        // Like SwiftUI's fixed frame: the art may overflow the 300dp slot rather than being squashed.
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.wrapContentHeight(unbounded = true)) { Illustration(page) }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (page == WelcomePage.WELCOME) {
                BrandLockup(size = 30.dp, modifier = Modifier.padding(bottom = Spacing.xs))
            } else {
                Overline(page.overline, tint = colors.brandOrange)
            }
            Text(page.title(denied), style = ZeroBiteTheme.type.largeTitle.semibold(), letterSpacing = (-1).sp, color = colors.textPrimary)
            Text(page.message(denied), style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        }
        Spacer(Modifier.size(Spacing.xl))
    }
}

private val WelcomePage.overline: String
    get() = when (this) {
        WelcomePage.WELCOME -> "Zero Bite"
        WelcomePage.SCAN -> "1 · Scan"
        WelcomePage.REMIND -> "2 · Get reminded"
        WelcomePage.COOK -> "3 · Cook"
        WelcomePage.REMINDERS -> "Last step"
    }

private fun WelcomePage.title(denied: Boolean): String = when (this) {
    WelcomePage.WELCOME -> "Waste less,\ncook more."
    WelcomePage.SCAN -> "Scan the label, skip the typing."
    WelcomePage.REMIND -> "Know what to use first."
    WelcomePage.COOK -> "Cook with what you have."
    WelcomePage.REMINDERS -> if (denied) "Reminders are off." else "Want a nudge before food goes off?"
}

private fun WelcomePage.message(denied: Boolean): String = when (this) {
    WelcomePage.WELCOME -> "Keep track of what's in your fridge, freezer and cupboard, and use it up before the date."
    WelcomePage.SCAN -> "Point the camera at a best-before or use-by date. A barcode fills in the product name. Everything is read on your phone."
    WelcomePage.REMIND -> "Your fridge is sorted by urgency. The most urgent item sits at the top, with one-tap Used it and Freeze."
    WelcomePage.COOK -> "Recipes are ranked by what's already in your fridge, with food that's about to expire counted first."
    WelcomePage.REMINDERS ->
        if (denied) "You can turn them on any time in Settings."
        else "We'll remind you a few days before the date, on the day, and if it slips past. You choose when."
}
