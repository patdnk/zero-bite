package com.mazesys.zerobite.screens.welcome

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
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
                    stringResource(ui.skipTitle),
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
                stringResource(ui.primaryTitle),
                { viewModel.primaryAction(requester) },
                Modifier.testTag("welcome.primary"),
                kind = if (onReminders) ButtonKind.BRAND else ButtonKind.INK,
                icon = if (onReminders && ui.step != WelcomeStep.DENIED) Icons.Rounded.NotificationsActive else null,
                loading = ui.isRequesting,
            )
            if (ui.showsNotNow) {
                AppButton(stringResource(R.string.action_not_now), viewModel::skip, Modifier.testTag("welcome.notNow"), kind = ButtonKind.PLAIN)
            }
        }
    }
}

/** Bespoke page dots: short bars, the current one stretched and orange. */
@Composable
private fun PageIndicator(count: Int, index: Int) {
    val colors = ZeroBiteTheme.colors
    val description = stringResource(R.string.welcome_page_indicator, index + 1, count)
    Row(
        Modifier.semantics { contentDescription = description },
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
                Overline(stringResource(page.overline), tint = colors.brandOrange)
            }
            Text(stringResource(page.title(denied)), style = ZeroBiteTheme.type.largeTitle.semibold(), letterSpacing = (-1).sp, color = colors.textPrimary)
            Text(stringResource(page.message(denied)), style = ZeroBiteTheme.type.body, color = colors.textSecondary)
        }
        Spacer(Modifier.size(Spacing.xl))
    }
}

@get:StringRes
private val WelcomePage.overline: Int
    get() = when (this) {
        WelcomePage.WELCOME -> R.string.app_name
        WelcomePage.SCAN -> R.string.welcome_overline_scan
        WelcomePage.REMIND -> R.string.welcome_overline_remind
        WelcomePage.COOK -> R.string.welcome_overline_cook
        WelcomePage.REMINDERS -> R.string.welcome_overline_reminders
    }

@StringRes
private fun WelcomePage.title(denied: Boolean): Int = when (this) {
    WelcomePage.WELCOME -> R.string.welcome_title_welcome
    WelcomePage.SCAN -> R.string.welcome_title_scan
    WelcomePage.REMIND -> R.string.welcome_title_remind
    WelcomePage.COOK -> R.string.welcome_title_cook
    WelcomePage.REMINDERS -> if (denied) R.string.welcome_title_reminders_denied else R.string.welcome_title_reminders
}

@StringRes
private fun WelcomePage.message(denied: Boolean): Int = when (this) {
    WelcomePage.WELCOME -> R.string.welcome_message_welcome
    WelcomePage.SCAN -> R.string.welcome_message_scan
    WelcomePage.REMIND -> R.string.welcome_message_remind
    WelcomePage.COOK -> R.string.welcome_message_cook
    WelcomePage.REMINDERS -> if (denied) R.string.welcome_message_reminders_denied else R.string.welcome_message_reminders
}
