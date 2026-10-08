package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.screens.welcome.WelcomeMode
import com.mazesys.zerobite.screens.welcome.WelcomeScreen
import com.mazesys.zerobite.screens.welcome.WelcomeViewModel
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppFlowController
import com.mazesys.zerobite.coordination.AppFlowPhase
import com.mazesys.zerobite.coordination.SheetViewModelStores
import kotlinx.coroutines.launch

/** Root driven by the app flow phase: launch → welcome → main. */
@Composable
fun RootScreen(flow: AppFlowController, coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    val phase by flow.phase.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Crossfade(phase, animationSpec = tween(300), label = "phase") { current ->
        when (current) {
            AppFlowPhase.LAUNCH -> LaunchScreen(onFinish = { scope.launch { flow.transitionFromLaunch() } })
            AppFlowPhase.WELCOME -> {
                val welcome = viewModel(key = "welcome-first-run") {
                    WelcomeViewModel(WelcomeMode.FIRST_RUN, dependencies.settings, dependencies.notifications, onComplete = { flow.transitionToMain() })
                }
                WelcomeScreen(welcome, Modifier.systemBarsPadding())
            }
            AppFlowPhase.MAIN -> MainScreen(coordinator, sheetStores, dependencies)
        }
    }
}
