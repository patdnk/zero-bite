package com.mazesys.zerobite.screens.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.AppTab
import com.mazesys.zerobite.coordination.InventoryCoordinator
import com.mazesys.zerobite.coordination.MainCoordinator
import com.mazesys.zerobite.coordination.SheetViewModelStores
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RootScreenTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: AppCoordinator

    /** The tabs, once the app has reached them. */
    private val tabs: MainCoordinator get() = app.main!!

    private fun show(settings: FakeSettings, inventory: FakeInventoryRepository = FakeInventoryRepository(sampleFridge())) {
        val dependencies = testDependencies(inventory = inventory, settings = settings)
        app = AppCoordinator(dependencies, CoroutineScope(Dispatchers.Unconfined))
        compose.setContent { ZeroBiteTheme { RootScreen(app, SheetViewModelStores(), dependencies) } }
    }

    private fun waitFor(text: String) = compose.waitUntil(3_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForTag(tag: String) = compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun pressBack() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `first run goes splash, welcome, fridge`() {
        val settings = FakeSettings()
        show(settings)
        compose.onNodeWithText("Zero Bite").assertExists()
        waitForTag("welcome.skip")
        compose.onNodeWithTag("welcome.skip").performClick()
        waitForTag("welcome.dontShare")
        compose.onNodeWithTag("welcome.dontShare").performClick()
        waitForTag("welcome.notNow")
        compose.onNodeWithTag("welcome.notNow").performClick()
        waitForTag("tab.fridge")
        compose.onNodeWithTag("tab.fridge").assertIsSelected()
        assertTrue(runBlocking { settings.current().hasCompletedWelcome })
        assertFalse(runBlocking { settings.current().shareUsageData })
    }

    @Test
    fun `returning user lands on the fridge`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)))
        waitForTag("tab.fridge")
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `tabs switch between screens`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)))
        waitForTag("tab.recipes")
        compose.onNodeWithTag("tab.recipes").performClick()
        compose.onNodeWithText("Nothing to cook yet").assertIsDisplayed()
        compose.onNodeWithTag("tab.fridge").performClick()
        compose.onNodeWithText("Greek yogurt").assertIsDisplayed()
    }

    @Test
    fun `adding by hand puts the item in the fridge`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)), FakeInventoryRepository())
        waitFor("Try it with sample food")
        compose.runOnUiThread { tabs.inventory.eventLoop(InventoryCoordinator.Event.DidTapAddItem(TestClock.today)) }
        waitForTag("itemForm.name")
        compose.onNodeWithTag("itemForm.name").performTextInput("Oat milk")
        compose.onNodeWithTag("itemForm.save").performClick()
        waitFor("Oat milk")
        assertNull(tabs.state.sheet)
    }

    @Test
    fun `back on another tab returns to the fridge before leaving`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)))
        waitForTag("tab.settings")
        compose.onNodeWithTag("tab.settings").performClick()
        compose.waitForIdle()
        pressBack()
        compose.waitUntil(3_000) { tabs.state.selectedTab == AppTab.FRIDGE }
        assertTrue(!compose.activity.isFinishing, "back on Settings returns to the Fridge tab")
    }

    @Test
    fun `back closes the sheet, then pops detail`() {
        show(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true, dismissedNotificationTip = true)))
        waitFor("Spinach")
        compose.onNodeWithText("Spinach").performClick()
        waitFor("Details")

        compose.runOnUiThread { tabs.inventory.eventLoop(InventoryCoordinator.Event.DidTapScan) }
        waitFor("Choose a photo of the label")
        pressBack()
        compose.waitUntil(3_000) { tabs.state.sheet == null }
        compose.onNodeWithText("Details").assertExists()

        pressBack()
        compose.waitUntil(3_000) { tabs.inventory.state.backStack.size == 1 }
        compose.waitForIdle() // let the pop transition finish, as a person would

        pressBack()
        assertTrue(compose.activity.isFinishing, "back at the root leaves the app")
        assertEquals(1, tabs.inventory.state.backStack.size)
    }
}
