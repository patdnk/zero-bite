package com.mazesys.zerobite.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.data.SettingsRepository
import com.mazesys.zerobite.reminders.NotificationAccess
import com.mazesys.zerobite.reminders.PermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WelcomeMode {
    /** First launch: explainer pages, then the notification opt-in. */
    FIRST_RUN,
    /** From Settings: explainer pages only. */
    REPLAY,
}

enum class WelcomePage { WELCOME, SCAN, REMIND, COOK, REMINDERS }

enum class WelcomeStep { INTRO, REQUESTING, DENIED }

data class WelcomeUiState(
    val mode: WelcomeMode,
    val pages: List<WelcomePage>,
    val currentPage: WelcomePage,
    val step: WelcomeStep = WelcomeStep.INTRO,
) {
    val pageIndex: Int get() = pages.indexOf(currentPage).coerceAtLeast(0)
    val isLastPage: Boolean get() = currentPage == pages.last()
    val isRequesting: Boolean get() = step == WelcomeStep.REQUESTING
    val skipTitle: String get() = if (mode == WelcomeMode.REPLAY) "Close" else "Skip"
    val showsNotNow: Boolean get() = currentPage == WelcomePage.REMINDERS && step != WelcomeStep.DENIED

    val primaryTitle: String
        get() = when (currentPage) {
            WelcomePage.REMINDERS -> if (step == WelcomeStep.DENIED) "Continue" else "Turn on reminders"
            WelcomePage.COOK -> if (mode == WelcomeMode.REPLAY) "Done" else "Next"
            WelcomePage.WELCOME -> "Show me how"
            else -> "Next"
        }
}

/** Onboarding: a short paged explainer, ending (on first run) with the reminder opt-in. */
class WelcomeViewModel(
    mode: WelcomeMode,
    private val settings: SettingsRepository,
    private val access: NotificationAccess,
    private val onComplete: suspend () -> Unit,
) : ViewModel() {

    private val pages = if (mode == WelcomeMode.FIRST_RUN) WelcomePage.entries else WelcomePage.entries - WelcomePage.REMINDERS
    private val _state = MutableStateFlow(WelcomeUiState(mode, pages, pages.first()))
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()
    private var finished = false

    /** Swiping the pager. */
    fun setPage(page: WelcomePage) {
        if (page in pages) _state.update { it.copy(currentPage = page) }
    }

    /** Advances, or finishes on the last page (asking for permission on first run). */
    fun primaryAction(requester: PermissionRequester) {
        val current = _state.value
        when {
            current.currentPage == WelcomePage.REMINDERS && current.step == WelcomeStep.DENIED -> finish()
            current.currentPage == WelcomePage.REMINDERS -> enableReminders(requester)
            current.isLastPage -> finish()
            else -> _state.update { it.copy(currentPage = pages[it.pageIndex + 1]) }
        }
    }

    /** Past the explainer: to the permission page on first run, or closes when replaying. */
    fun skipIntro() {
        if (WelcomePage.REMINDERS in pages) setPage(WelcomePage.REMINDERS) else finish()
    }

    /** "Not now" on the reminders page. */
    fun skip() {
        viewModelScope.launch {
            settings.update { it.copy(notificationsEnabled = false) }
            complete()
        }
    }

    private fun enableReminders(requester: PermissionRequester) {
        _state.update { it.copy(step = WelcomeStep.REQUESTING) }
        viewModelScope.launch {
            val granted = requester.requestNotifications()
            access.markAsked()
            settings.update { it.copy(notificationsEnabled = granted) }
            if (granted) complete() else _state.update { it.copy(step = WelcomeStep.DENIED) }
        }
    }

    private fun finish() {
        viewModelScope.launch { complete() }
    }

    private suspend fun complete() {
        if (finished) return
        finished = true
        onComplete()
    }
}
