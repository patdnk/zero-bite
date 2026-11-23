package com.mazesys.zerobite

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.coordination.MainCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.reminders.Notifications
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(application = ZeroBiteApplication::class)
class DeepLinkTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `notification tap opens the item from anywhere`() {
        val activity = compose.activity
        val app = ViewModelProvider(activity, AppViewModel.factory((activity.application as ZeroBiteApplication).dependencies))[AppViewModel::class.java]
        // Straight to the tabs, whichever root screen is up. The splash's settings read and the
        // welcome save both need real time on DataStore's thread, which Robolectric's clock doesn't
        // give, so the test reports their results itself.
        compose.runOnUiThread {
            app.coordinator.eventLoop(AppCoordinator.Event.DidFinishLaunch(hasCompletedWelcome = true))
            app.coordinator.eventLoop(AppCoordinator.Event.DidRememberWelcomeCompleted)
        }
        val main = assertNotNull(app.coordinator.main)

        compose.runOnUiThread {
            main.eventLoop(MainCoordinator.Event.DidSelectTab(AppTab.SETTINGS))
            main.eventLoop(MainCoordinator.Event.DidRequestSheet(SheetDestination.Scan))
            activity.handleIntent(Intent(activity, MainActivity::class.java).putExtra(Notifications.EXTRA_ITEM_ID, "milk"))
        }
        assertEquals(AppTab.FRIDGE, main.state.selectedTab)
        assertNull(main.state.sheet)
        assertEquals(InventoryRoute.ItemDetail("milk"), main.inventory.state.backStack.last())
    }
}
