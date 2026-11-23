package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.coordination.MainCoordinator.Command
import com.mazesys.zerobite.coordination.MainCoordinator.Event
import com.mazesys.zerobite.coordination.MainCoordinator.State
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.english
import com.mazesys.zerobite.testing.sampleFridge
import com.mazesys.zerobite.testing.testDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Main's tabs and sheets: its state machine, then its children's, then all of them wired together. */
class MainStateMachineTest {

    /** The state after [events], and the commands the last one produced. */
    private fun run(vararg events: Event, from: State = State()): Pair<State, List<Command>> {
        val replay = MainCoordinator.replay(events.toList(), from)
        return replay.state to (replay.commands.lastOrNull() ?: emptyList())
    }

    @Test
    fun `the scan sheet starts a scan and closing it stops it`() {
        val (open, start) = run(Event.DidRequestSheet(SheetDestination.Scan))
        assertEquals(SheetDestination.Scan, open.sheet?.destination)
        assertEquals(listOf<Command>(Command.WillStartScan), start)

        val (closed, stop) = run(Event.DidDismissSheet, from = open)
        assertNull(closed.sheet)
        assertEquals(listOf<Command>(Command.WillStopScan), stop)
    }

    @Test
    fun `other sheets need no child`() {
        val (state, commands) = run(Event.DidRequestSheet(SheetDestination.EditItem("milk")))
        assertEquals(SheetDestination.EditItem("milk"), state.sheet?.destination)
        assertTrue(commands.isEmpty())
        assertTrue(run(Event.DidDismissSheet, from = state).second.isEmpty())
    }

    @Test
    fun `only one sheet at a time`() {
        val (state, commands) = run(Event.DidRequestSheet(SheetDestination.EditItem("milk")), Event.DidRequestSheet(SheetDestination.Scan))
        assertEquals(SheetDestination.EditItem("milk"), state.sheet?.destination)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `each presentation gets its own id`() {
        val (first, _) = run(Event.DidRequestSheet(SheetDestination.HowItWorks))
        val (second, _) = run(Event.DidDismissSheet, Event.DidRequestSheet(SheetDestination.HowItWorks), from = first)
        assertNotEquals(first.sheet?.id, second.sheet?.id)
    }

    @Test
    fun `a finished scan only closes the scan sheet`() {
        val (state, _) = run(Event.DidRequestSheet(SheetDestination.EditItem("milk")), Event.DidFinishScan)
        assertEquals(SheetDestination.EditItem("milk"), state.sheet?.destination)
    }

    @Test
    fun `a requested item closes whatever is open and shows the fridge`() {
        val (state, commands) = run(Event.DidSelectTab(AppTab.SETTINGS), Event.DidRequestSheet(SheetDestination.Scan), Event.DidRequestItem("milk"))
        assertEquals(AppTab.FRIDGE, state.selectedTab)
        assertNull(state.sheet)
        assertEquals(listOf(Command.WillStopScan, Command.WillShowItem("milk")), commands)
    }
}

class TabStateMachineTest {

    @Test
    fun `inventory pushes pages and asks main for sheets`() {
        val replay = InventoryCoordinator.replay(
            listOf(
                InventoryCoordinator.Event.DidSelectItem("milk"),
                InventoryCoordinator.Event.DidSelectRecipe("pancakes"),
                InventoryCoordinator.Event.DidDeleteItem,
                InventoryCoordinator.Event.DidTapAddItem(TestClock.today),
            ),
            from = InventoryCoordinator.State(),
        )
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), replay.state.backStack)
        val present = assertIs<InventoryCoordinator.Command.WillPresent>(replay.commands.last().single())
        val add = assertIs<SheetDestination.AddItem>(present.destination)
        assertEquals(LocalDate(2026, 9, 14), add.draft.expiresOn, "a week-long default date")
    }

    @Test
    fun `a requested item replaces the fridge stack`() {
        val replay = InventoryCoordinator.replay(
            listOf(InventoryCoordinator.Event.DidSelectRecipe("pancakes"), InventoryCoordinator.Event.DidRequestItem("milk")),
            from = InventoryCoordinator.State(),
        )
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), replay.state.backStack)
    }

    @Test
    fun `back never removes the root`() {
        val fridge = InventoryCoordinator.replay(
            listOf(InventoryCoordinator.Event.DidTapBack, InventoryCoordinator.Event.DidSelectItem("a"), InventoryCoordinator.Event.DidTapBack, InventoryCoordinator.Event.DidTapBack),
            from = InventoryCoordinator.State(),
        )
        assertEquals(listOf<InventoryRoute>(InventoryRoute.Fridge), fridge.state.backStack)

        val recipes = RecipesCoordinator.replay(listOf(RecipesCoordinator.Event.DidTapBack), from = RecipesCoordinator.State())
        assertEquals(listOf<RecipesRoute>(RecipesRoute.Catalogue), recipes.state.backStack)
    }

    @Test
    fun `recipes asks main for the cooked sheet`() {
        val replay = RecipesCoordinator.replay(
            listOf(RecipesCoordinator.Event.DidSelectRecipe("pancakes"), RecipesCoordinator.Event.DidTapCooked("pancakes")),
            from = RecipesCoordinator.State(),
        )
        assertEquals(listOf(RecipesRoute.Catalogue, RecipesRoute.RecipeDetail("pancakes")), replay.state.backStack)
        assertEquals(listOf<RecipesCoordinator.Command>(RecipesCoordinator.Command.WillPresent(SheetDestination.Cooked("pancakes"))), replay.commands.last())
    }

    @Test
    fun `settings confirms before deleting`() {
        val asked = SettingsCoordinator.replay(listOf(SettingsCoordinator.Event.DidTapDeleteEverything), from = SettingsCoordinator.State())
        assertEquals(SettingsAlert.ConfirmDeleteEverything, asked.state.alert)
        assertEquals(listOf(emptyList<SettingsCoordinator.Command>()), asked.commands)

        val confirmed = SettingsCoordinator.replay(listOf(SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.ConfirmDeleteEverything)), from = asked.state)
        assertNull(confirmed.state.alert)
        assertEquals(listOf(listOf<SettingsCoordinator.Command>(SettingsCoordinator.Command.WillDeleteEverything)), confirmed.commands)

        // The dialog may be cleared before the button's action runs.
        val clearedFirst = SettingsCoordinator.replay(
            listOf(SettingsCoordinator.Event.DidDismissAlert, SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.ConfirmDeleteEverything)),
            from = asked.state,
        )
        assertEquals(listOf(emptyList(), listOf<SettingsCoordinator.Command>(SettingsCoordinator.Command.WillDeleteEverything)), clearedFirst.commands)

        val cancelled = SettingsCoordinator.replay(listOf(SettingsCoordinator.Event.DidDismissAlert, SettingsCoordinator.Event.DidDismissAlert), from = asked.state)
        assertEquals(listOf(emptyList<SettingsCoordinator.Command>(), emptyList()), cancelled.commands)
    }

    @Test
    fun `clearing removes exactly the items the message counted`() {
        val ids = listOf("a", "b")
        val replay = SettingsCoordinator.replay(
            listOf(SettingsCoordinator.Event.DidTapClearUsedHistory(ids), SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.ConfirmClearUsedHistory(ids))),
            from = SettingsCoordinator.State(),
        )
        assertEquals(listOf<SettingsCoordinator.Command>(SettingsCoordinator.Command.WillClearUsedHistory(ids)), replay.commands.last())
    }

    @Test
    fun `nothing to clear asks nothing`() {
        val replay = SettingsCoordinator.replay(listOf(SettingsCoordinator.Event.DidTapClearUsedHistory(emptyList())), from = SettingsCoordinator.State())
        assertNull(replay.state.alert)
    }

    @Test
    fun `an info message confirms nothing`() {
        val replay = SettingsCoordinator.replay(
            listOf(SettingsCoordinator.Event.DidFindNotificationsDenied, SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.NotificationsOff)),
            from = SettingsCoordinator.State(),
        )
        assertNull(replay.state.alert)
        assertEquals(listOf(emptyList<SettingsCoordinator.Command>(), emptyList()), replay.commands)
    }

    @Test
    fun `alert text comes from resources`() {
        assertEquals("Notifications are off", SettingsAlert.NotificationsOff.title.english())
        assertEquals("Removes 2 items you've already marked as used.", SettingsAlert.ConfirmClearUsedHistory(listOf("a", "b")).message.english())
        assertEquals("Delete all", SettingsAlert.ConfirmDeleteEverything.actionTitle?.english())
        assertNull(SettingsAlert.NotificationsOff.actionTitle)
    }
}

/** Children and parent wired together, as the app runs them. */
@OptIn(ExperimentalCoroutinesApi::class)
class CoordinatorTreeTest {

    private fun CoroutineScope.makeMain(inventory: FakeInventoryRepository = FakeInventoryRepository()) =
        MainCoordinator(parent = null, dependencies = testDependencies(inventory = inventory), scope = this)

    @Test
    fun `a tab's sheet request opens the sheet on main`() = runTest(UnconfinedTestDispatcher()) {
        val main = backgroundScope.makeMain()
        main.recipes.eventLoop(RecipesCoordinator.Event.DidTapEditItem("milk"))
        assertEquals(SheetDestination.EditItem("milk"), main.state.sheet?.destination)
    }

    @Test
    fun `a finished scan closes the scan sheet`() = runTest(UnconfinedTestDispatcher()) {
        val main = backgroundScope.makeMain()
        main.inventory.eventLoop(InventoryCoordinator.Event.DidTapScan)
        assertEquals(SheetDestination.Scan, main.state.sheet?.destination)
        main.eventLoop(Event.DidFinishScan)
        assertNull(main.state.sheet)
    }

    @Test
    fun `a requested item resets the fridge and closes settings' alert`() = runTest(UnconfinedTestDispatcher()) {
        val main = backgroundScope.makeMain()
        main.eventLoop(Event.DidSelectTab(AppTab.SETTINGS))
        main.inventory.eventLoop(InventoryCoordinator.Event.DidSelectItem("old"))
        main.settings.eventLoop(SettingsCoordinator.Event.DidTapDeleteEverything)

        main.eventLoop(Event.DidRequestItem("milk"))

        assertEquals(AppTab.FRIDGE, main.state.selectedTab)
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), main.inventory.state.backStack)
        assertNull(main.settings.state.alert)
    }

    @Test
    fun `confirming delete everything empties the repository`() = runTest(UnconfinedTestDispatcher()) {
        val inventory = FakeInventoryRepository(sampleFridge())
        val main = backgroundScope.makeMain(inventory)
        main.settings.eventLoop(SettingsCoordinator.Event.DidTapDeleteEverything).join()
        assertTrue(inventory.current.isNotEmpty())
        main.settings.eventLoop(SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.ConfirmDeleteEverything)).join()
        assertTrue(inventory.current.isEmpty())
    }

    @Test
    fun `clearing used history removes only the counted items`() = runTest(UnconfinedTestDispatcher()) {
        val inventory = FakeInventoryRepository(sampleFridge())
        val main = backgroundScope.makeMain(inventory)
        val (used, kept) = inventory.current.take(2).map { it.id }
        main.settings.eventLoop(SettingsCoordinator.Event.DidConfirmAlert(SettingsAlert.ConfirmClearUsedHistory(listOf(used)))).join()
        assertTrue(inventory.current.none { it.id == used })
        assertTrue(inventory.current.any { it.id == kept })
    }

    @Test
    fun `settings asks main to replay how it works`() = runTest(UnconfinedTestDispatcher()) {
        val main = backgroundScope.makeMain()
        main.settings.eventLoop(SettingsCoordinator.Event.DidTapHowItWorks)
        assertEquals(SheetDestination.HowItWorks, main.state.sheet?.destination)
    }

    @Test
    fun `an ignored event shows up in the history`() = runTest(UnconfinedTestDispatcher()) {
        val main = backgroundScope.makeMain()
        main.eventLoop(Event.DidDismissSheet) // nothing open
        assertTrue(main.history.last().wasIgnored)
    }
}
