package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.testing.english
import com.mazesys.zerobite.ui.text.uiText
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.TestClock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoordinationTest {

    @Test
    fun `launch goes to welcome until it has been completed`() = runBlocking {
        val settings = FakeSettings()
        val flow = AppFlowController(settings)
        assertEquals(AppFlowPhase.LAUNCH, flow.phase.value)
        flow.transitionFromLaunch()
        assertEquals(AppFlowPhase.WELCOME, flow.phase.value)
        flow.transitionToMain()
        assertEquals(AppFlowPhase.MAIN, flow.phase.value)
        assertTrue(settings.current().hasCompletedWelcome)

        val returning = AppFlowController(FakeSettings(SettingsSnapshot(hasCompletedWelcome = true)))
        returning.transitionFromLaunch()
        assertEquals(AppFlowPhase.MAIN, returning.phase.value)
    }

    @Test
    fun `add item presents a sheet with a week-long default date`() {
        val app = AppCoordinator { TestClock.today }
        app.inventory.showAddItem()
        val sheet = app.presentedSheet!!
        val destination = assertIs<SheetDestination.AddItem>(sheet.destination)
        assertEquals(LocalDate(2026, 9, 14), destination.draft.expiresOn)

        app.inventory.showAddItem()
        assertTrue(app.presentedSheet!!.id != sheet.id, "each presentation gets its own id")
        app.dismissSheet()
        assertNull(app.presentedSheet)
    }

    @Test
    fun `deep link to an item resets to the fridge and opens it`() {
        val app = AppCoordinator { TestClock.today }
        app.selectTab(AppTab.SETTINGS)
        app.inventory.showItem("old")
        app.presentSheet(SheetDestination.Scan)

        app.showItem("milk")

        assertNull(app.presentedSheet)
        assertEquals(AppTab.FRIDGE, app.selectedTab)
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), app.inventory.backStack.toList())
    }

    @Test
    fun `popping never removes the fridge root`() {
        val app = AppCoordinator { TestClock.today }
        app.inventory.pop()
        assertEquals(listOf<InventoryRoute>(InventoryRoute.Fridge), app.inventory.backStack.toList())
        app.inventory.showItem("a")
        app.inventory.pop()
        assertEquals(listOf<InventoryRoute>(InventoryRoute.Fridge), app.inventory.backStack.toList())
    }

    @Test
    fun `alerts show and dismiss`() {
        val app = AppCoordinator { TestClock.today }
        app.showAlert(AlertConfig(uiText(R.string.detail_delete_title), primary = AlertButton(uiText(R.string.action_delete), isDestructive = true)))
        assertEquals("Delete this item?", app.alert?.title?.english())
        app.dismissAlert()
        assertNull(app.alert)
    }

    @Test
    fun `deep link closes any open alert`() {
        val app = AppCoordinator { TestClock.today }
        app.showAlert(AlertConfig(uiText(R.string.alert_delete_everything_title), primary = AlertButton(uiText(R.string.alert_delete_everything_action), isDestructive = true)))
        app.showItem("milk")
        assertNull(app.alert)
    }

    @Test
    fun `a double tap does not push the same scan route twice`() {
        val scan = ScanCoordinator(onFinished = {})
        val result = ScanResult(lines = listOf("MILK"))
        scan.showLiveScanner()
        scan.showLiveScanner()
        scan.showReview(result)
        scan.showReview(result)
        val draft = ItemDraft(name = "Milk", expiresOn = TestClock.today)
        scan.showForm(draft)
        scan.showForm(draft)

        assertEquals(
            listOf(ScanRoute.Choose, ScanRoute.Live, ScanRoute.Review(result), ScanRoute.Form(draft)),
            scan.backStack.toList(),
        )
    }
}
