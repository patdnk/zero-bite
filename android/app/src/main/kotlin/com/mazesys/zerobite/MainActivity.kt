package com.mazesys.zerobite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mazesys.zerobite.screens.root.RootScreen
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

class MainActivity : ComponentActivity() {

    private val dependencies: AppDependencies get() = (application as ZeroBiteApplication).dependencies
    private val app: AppViewModel by viewModels { AppViewModel.factory(dependencies) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZeroBiteTheme {
                RootScreen(app.flow, app.coordinator, app.sheetStores, dependencies)
            }
        }
    }
}
