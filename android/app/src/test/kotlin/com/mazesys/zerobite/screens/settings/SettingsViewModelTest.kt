package com.mazesys.zerobite.screens.settings

import com.mazesys.zerobite.testing.english
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeNotifier
import com.mazesys.zerobite.testing.FakeRecipes
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import com.mazesys.zerobite.testing.testRecipes
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val app = AppCoordinator { TestClock.today }
    private val inventory = FakeInventoryRepository(sampleFridge())
    private val settings = FakeSettings()
    private val access = FakeAccess(NotificationPermission.NOT_ASKED)
    private val notifier = FakeNotifier()
    private val vm by lazy {
        SettingsViewModel(
        testDependencies(inventory = inventory, settings = settings, recipes = FakeRecipes(testRecipes()), access = access, notifier = notifier),
        app.settings,
        appVersion = "1.0 (1)",
        )
    }

    @Test
    fun `notification state follows the system permission`() = runBlocking {
        assertEquals(ReminderState.OFF, vm.state.value.reminderState)
        settings.update { it.copy(notificationsEnabled = true) }
        access.current = NotificationPermission.GRANTED
        vm.refreshPermission()
        assertEquals(ReminderState.ON, vm.state.value.reminderState)
        access.current = NotificationPermission.DENIED // switched off in system settings
        vm.refreshPermission()
        assertEquals(ReminderState.DENIED, vm.state.value.reminderState)
    }

    @Test
    fun `turning reminders on asks, and explains a refusal`() {
        vm.setNotificationsEnabled(true) { access.current = NotificationPermission.GRANTED; true }
        assertTrue(settings.state.value.notificationsEnabled)
        assertEquals(ReminderState.ON, vm.state.value.reminderState)

        vm.setNotificationsEnabled(false) { true }
        assertFalse(settings.state.value.notificationsEnabled)

        access.current = NotificationPermission.DENIED
        vm.setNotificationsEnabled(true) { false }
        assertFalse(settings.state.value.notificationsEnabled)
        assertEquals("Notifications are off", app.alert?.title?.english())
    }

    @Test
    fun `steppers clamp to one to fourteen days`() {
        vm.setLeadDays(0)
        assertEquals(1, settings.state.value.leadDays)
        vm.setLeadDays(99)
        assertEquals(14, settings.state.value.leadDays)
        vm.setSoonThresholdDays(-3)
        assertEquals(1, settings.state.value.soonThresholdDays)
        vm.setSoonThresholdDays(15)
        assertEquals(14, settings.state.value.soonThresholdDays)
    }

    @Test
    fun `reminder time accepts the whole day`() {
        vm.setNotifyTime(0, 0)
        assertEquals("00:00", vm.state.value.notifyTimeText)
        vm.setNotifyTime(23, 59)
        assertEquals("23:59", vm.state.value.notifyTimeText)
    }

    @Test
    fun `allergens and recall checks are saved`() {
        vm.toggleAllergen(Allergen.MILK)
        vm.toggleAllergen(Allergen.PEANUTS)
        vm.toggleAllergen(Allergen.MILK)
        assertEquals(setOf("peanuts"), settings.state.value.allergens)
        vm.setRecallChecksEnabled(false)
        assertFalse(settings.state.value.recallChecksEnabled)
    }

    @Test
    fun `sharing usage data is off until switched on`() {
        assertFalse(vm.state.value.snapshot.shareUsageData)
        vm.setShareUsageData(true)
        assertTrue(settings.state.value.shareUsageData)
        assertTrue(vm.state.value.snapshot.shareUsageData)
        vm.setShareUsageData(false)
        assertFalse(settings.state.value.shareUsageData)
    }

    @Test
    fun `data actions confirm before deleting`() = runBlocking {
        assertEquals(21, vm.state.value.activeCount)
        assertEquals(4, vm.state.value.recipeCount)
        inventory.markConsumed(listOf(inventory.current.first().id), TestClock.now)
        assertEquals(1, vm.state.value.usedCount)

        vm.clearUsedHistory()
        assertEquals("Clear used-up history?", app.alert?.title?.english())
        assertEquals("Removes 1 items you've already marked as used.", app.alert?.message?.english())
        app.alert!!.primary.action()
        assertEquals(0, vm.state.value.usedCount)
        assertEquals(20, vm.state.value.activeCount)

        vm.deleteEverything()
        assertEquals("Delete everything?", app.alert?.title?.english())
        app.alert!!.primary.action()
        assertEquals(0, vm.state.value.activeCount)
    }

    @Test
    fun `test notification, sample food and how it works`() {
        vm.sendTest()
        assertEquals(1, notifier.tests)
        vm.showHowItWorks()
        assertEquals(SheetDestination.HowItWorks, app.presentedSheet?.destination)
        runBlocking { inventory.deleteAll() }
        vm.loadSampleData()
        assertEquals(21, vm.state.value.activeCount)
    }
}
