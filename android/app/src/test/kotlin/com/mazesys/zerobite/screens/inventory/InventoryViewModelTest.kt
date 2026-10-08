package com.mazesys.zerobite.screens.inventory

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.reminders.NotificationPermission
import com.mazesys.zerobite.coordination.AppCoordinator
import com.mazesys.zerobite.coordination.InventoryRoute
import com.mazesys.zerobite.coordination.SheetDestination
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.MainDispatcherRule
import com.mazesys.zerobite.testing.SequentialIds
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.sampleFridge
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val inventory = FakeInventoryRepository(sampleFridge())
    private val app = AppCoordinator { TestClock.today }

    private fun viewModel(repository: FakeInventoryRepository = inventory) =
        InventoryViewModel(repository, FakeSettings(SettingsSnapshot(dismissedNotificationTip = true)), TestClock.fixed(), SequentialIds(), app.inventory, FakeAccess())

    private fun InventoryUiState.gridNames() = gridSections.flatMap { it.items }.map { it.name }

    @Test
    fun `sections are ordered by urgency and the hero leaves the grid`() {
        val state = viewModel().state.value
        assertEquals("Greek yogurt", state.useFirstItem?.name)
        val statuses = state.gridSections.map { it.status }
        assertEquals(statuses.sorted(), statuses)
        assertFalse("Greek yogurt" in state.gridNames())
        assertEquals(state.attentionCount - 1, state.othersNeedingAttention)
        assertEquals("Mon 7 Sep", state.headerDate)
    }

    @Test
    fun `search and storage filter narrow the list`() {
        val vm = viewModel()
        vm.setSearchText("milk")
        assertEquals(listOf("Semi-skimmed milk"), vm.state.value.gridNames())
        assertNull(vm.state.value.useFirstItem, "no hero while searching")

        vm.setSearchText("")
        vm.setStorageFilter(StorageFilter.FREEZER)
        assertEquals(listOf("Salmon fillets"), vm.state.value.gridNames())
    }

    @Test
    fun `an empty search result explains itself`() {
        val vm = viewModel()
        vm.setSearchText("caviar")
        assertEquals(emptyList(), vm.state.value.gridNames())
        assertEquals("Nothing matches “caviar”.", vm.state.value.emptyGridMessage)
    }

    @Test
    fun `freezing moves to the freezer for at least three months`() {
        val vm = viewModel()
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.freeze(spinach)
        val frozen = inventory.current.first { it.id == spinach.id }
        assertEquals(StorageLocation.FREEZER, frozen.storage)
        assertEquals(LocalDate(2026, 12, 7), frozen.expiresOn)
        assertFalse(vm.state.value.canFreeze(frozen))

        val yogurt = inventory.current.first { it.name == "Greek yogurt" }
        assertFalse(vm.state.value.canFreeze(yogurt), "expired food can't be frozen")
        vm.freeze(yogurt)
        assertEquals(StorageLocation.FRIDGE, inventory.current.first { it.id == yogurt.id }.storage)
    }

    @Test
    fun `marking used removes it from the active list`() {
        val vm = viewModel()
        val before = vm.state.value.activeItems.size
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.markUsed(spinach)
        assertEquals(before - 1, vm.state.value.activeItems.size)
        assertEquals(TestClock.now, inventory.current.first { it.id == spinach.id }.consumedAt)
    }

    @Test
    fun `an empty fridge can load the sample food`() {
        val vm = viewModel(FakeInventoryRepository())
        assertTrue(vm.state.value.isEmpty)
        vm.loadSampleData()
        assertEquals(21, vm.state.value.activeItems.size)
    }

    @Test
    fun `actions route through the coordinator`() {
        val vm = viewModel()
        val spinach = inventory.current.first { it.name == "Spinach" }
        vm.showDetails(spinach)
        assertEquals(InventoryRoute.ItemDetail(spinach.id), app.inventory.backStack.last())
        vm.showAddItem()
        assertIs<SheetDestination.AddItem>(app.presentedSheet?.destination)
        vm.showEditItem(spinach)
        assertEquals(SheetDestination.EditItem(spinach.id), app.presentedSheet?.destination)
        vm.showScan()
        assertEquals(SheetDestination.Scan, app.presentedSheet?.destination)
    }

    private fun tipViewModel(access: FakeAccess, settings: FakeSettings = FakeSettings()) =
        InventoryViewModel(inventory, settings, TestClock.fixed(), SequentialIds(), app.inventory, access)

    @Test
    fun `reminder tip shows until answered`() {
        val vm = tipViewModel(FakeAccess(NotificationPermission.NOT_ASKED))
        assertTrue(vm.state.value.showsNotificationTip)
        vm.dismissNotificationTip()
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `turning reminders on from the tip asks for permission`() {
        val access = FakeAccess(NotificationPermission.NOT_ASKED)
        val settings = FakeSettings()
        val vm = tipViewModel(access, settings)
        vm.enableNotifications { access.current = NotificationPermission.GRANTED; true }
        assertTrue(access.asked)
        assertTrue(settings.state.value.notificationsEnabled)
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `refusing hides the tip and leaves reminders off`() {
        val access = FakeAccess(NotificationPermission.NOT_ASKED)
        val settings = FakeSettings()
        val vm = tipViewModel(access, settings)
        vm.enableNotifications { access.current = NotificationPermission.DENIED; false }
        assertFalse(settings.state.value.notificationsEnabled)
        assertFalse(vm.state.value.showsNotificationTip)
    }

    @Test
    fun `no tip once permission was refused at the system level`() {
        assertFalse(tipViewModel(FakeAccess(NotificationPermission.DENIED)).state.value.showsNotificationTip)
    }
}
