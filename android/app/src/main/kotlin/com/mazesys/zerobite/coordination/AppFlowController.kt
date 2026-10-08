package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which root screen is shown: the single source of truth for app-level flow. */
enum class AppFlowPhase { LAUNCH, WELCOME, MAIN }

class AppFlowController(private val settings: SettingsRepository) {

    private val _phase = MutableStateFlow(AppFlowPhase.LAUNCH)
    val phase: StateFlow<AppFlowPhase> = _phase.asStateFlow()

    /** Call when the splash is done. Skips the welcome flow once it has been completed. */
    suspend fun transitionFromLaunch() {
        _phase.value = if (settings.current().hasCompletedWelcome) AppFlowPhase.MAIN else AppFlowPhase.WELCOME
    }

    /** Call when the welcome flow finishes. */
    suspend fun transitionToMain() {
        settings.update { it.copy(hasCompletedWelcome = true) }
        _phase.value = AppFlowPhase.MAIN
    }

    /** Re-run the welcome flow (from Settings, in Plan 3). */
    fun transitionToWelcome() {
        _phase.value = AppFlowPhase.WELCOME
    }
}
