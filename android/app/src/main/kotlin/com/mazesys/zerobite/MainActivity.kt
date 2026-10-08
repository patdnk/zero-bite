package com.mazesys.zerobite

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.reminders.Notifications
import com.mazesys.zerobite.reminders.PermissionRequester
import com.mazesys.zerobite.screens.root.RootScreen
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val dependencies: AppDependencies get() = (application as ZeroBiteApplication).dependencies
    private val app: AppViewModel by viewModels { AppViewModel.factory(dependencies) }

    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.complete(granted)
        pendingPermission = null
    }

    private val requester = PermissionRequester {
        if (Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        } else {
            val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            result.await()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ZeroBiteTheme {
                CompositionLocalProvider(LocalPermissionRequester provides requester) {
                    RootScreen(app.flow, app.coordinator, app.sheetStores, dependencies)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Notification permission may have been changed in system settings meanwhile.
        lifecycleScope.launch { dependencies.reminders.resyncNow() }
    }

    /** A reminder tap: jump to the Fridge tab and open the item, from wherever the app was. */
    fun handleIntent(intent: Intent) {
        val itemId = intent.getStringExtra(Notifications.EXTRA_ITEM_ID) ?: return
        intent.removeExtra(Notifications.EXTRA_ITEM_ID)
        app.coordinator.showItem(itemId)
    }
}
