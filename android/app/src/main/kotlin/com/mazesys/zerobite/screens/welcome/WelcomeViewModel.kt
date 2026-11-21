package com.mazesys.zerobite.screens.welcome

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.mazesys.zerobite.R
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
    /** First launch: explainer pages, then the usage data and notification opt-ins. */
    FIRST_RUN,
    /** From Settings: explainer pages only. */
    REPLAY,
}

enum class WelcomePage { WELCOME, SCAN, REMIND, COOK, SHARE, REMINDERS }

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
    /** Only on the explainer pages; the questions need an answer. */
    val showsSkip: Boolean get() = !isLastPage && currentPage != WelcomePage.SHARE
    @get:StringRes val skipTitle: Int get() = if (mode == WelcomeMode.REPLAY) R.string.action_close else R.string.action_skip
    val showsNotNow: Boolean get() = currentPage == WelcomePage.REMINDERS && step != WelcomeStep.DENIED

    @get:StringRes
    val primaryTitle: Int
        get() = when (currentPage) {
            WelcomePage.REMINDERS -> if (step == WelcomeStep.DENIED) R.string.action_continue else R.string.welcome_turn_on_reminders
            WelcomePage.SHARE -> R.string.welcome_share_usage_data
            WelcomePage.COOK -> if (mode == WelcomeMode.REPLAY) R.string.action_done else R.string.action_next
            WelcomePage.WELCOME -> R.string.welcome_show_me_how
            else -> R.string.action_next
        }
}

/** Onboarding: a short paged explainer, ending (on first run) with the usage data and reminder opt-ins. */
class WelcomeViewModel(
    mode: WelcomeMode,
    private val settings: SettingsRepository,
    private val access: NotificationAccess,
    private val onComplete: suspend () -> Unit,
) : ViewModel() {

    private val pages = if (mode == WelcomeMode.FIRST_RUN) WelcomePage.entries else WelcomePage.entries - QUESTIONS
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
            current.currentPage == WelcomePage.SHARE -> answerSharing(true)
            current.isLastPage -> finish()
            else -> _state.update { it.copy(currentPage = pages[it.pageIndex + 1]) }
        }
    }

    /** Past the explainer: to the first question on first run, or closes when replaying. */
    fun skipIntro() {
        pages.firstOrNull { it in QUESTIONS }?.let(::setPage) ?: finish()
    }

    /** "Don't share" on the usage data page. */
    fun declineSharing() = answerSharing(false)

    /** "Not now" on the reminders page. */
    fun skip() {
        viewModelScope.launch {
            settings.update { it.copy(notificationsEnabled = false) }
            complete()
        }
    }

    private fun answerSharing(share: Boolean) {
        setPage(WelcomePage.REMINDERS)
        viewModelScope.launch { settings.update { it.copy(shareUsageData = share) } }
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

    private companion object {
        /** First-run pages that ask something; replay leaves them out. */
        val QUESTIONS = listOf(WelcomePage.SHARE, WelcomePage.REMINDERS)
    }
}
