package com.mazesys.zerobite

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.reminders.Notifications
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
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
        compose.runOnUiThread {
            app.coordinator.selectTab(AppTab.SETTINGS)
            app.coordinator.presentSheet(SheetDestination.Scan)
            activity.handleIntent(Intent(activity, MainActivity::class.java).putExtra(Notifications.EXTRA_ITEM_ID, "milk"))
        }
        assertEquals(AppTab.FRIDGE, app.coordinator.selectedTab)
        assertNull(app.coordinator.presentedSheet)
        assertEquals(InventoryRoute.ItemDetail("milk"), app.coordinator.inventory.backStack.last())
    }
}
