package com.mazesys.zerobite.screens.root

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mazesys.zerobite.AppDependencies
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.screens.welcome.WelcomeMode
import com.mazesys.zerobite.screens.welcome.WelcomeScreen
import com.mazesys.zerobite.screens.welcome.WelcomeViewModel

/** Root driven by AppCoordinator's phase: launch → welcome → main. */
@Composable
fun RootScreen(coordinator: AppCoordinator, sheetStores: SheetViewModelStores, dependencies: AppDependencies) {
    Crossfade(coordinator.state.phase, animationSpec = tween(300), label = "phase") { phase ->
        when (phase) {
            AppCoordinator.Phase.LAUNCH -> LaunchScreen(onFinish = { coordinator.finishLaunch() })
            AppCoordinator.Phase.WELCOME -> {
                val welcome = viewModel(key = "welcome-first-run") {
                    WelcomeViewModel(WelcomeMode.FIRST_RUN, dependencies.settings, dependencies.notifications, onComplete = {
                        coordinator.eventLoop(AppCoordinator.Event.DidFinishWelcome)
                    })
                }
                WelcomeScreen(welcome, Modifier.systemBarsPadding())
            }
            AppCoordinator.Phase.MAIN -> coordinator.main?.let { MainScreen(it, sheetStores, dependencies) }
        }
    }
}
