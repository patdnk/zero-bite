package com.mazesys.zerobite.coordination

import com.mazesys.zerobite.coordination.AppCoordinator.Command
import com.mazesys.zerobite.coordination.AppCoordinator.Event
import com.mazesys.zerobite.coordination.AppCoordinator.Phase
import com.mazesys.zerobite.coordination.AppCoordinator.State
import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeScheduler
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.testDependencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStateMachineTest {

    /** The state after [events], and the commands the last one produced. */
    private fun run(vararg events: Event, from: State = State()): Pair<State, List<Command>> {
        val replay = AppCoordinator.replay(events.toList(), from)
        return replay.state to (replay.commands.lastOrNull() ?: emptyList())
    }

    @Test
    fun `first launch goes to welcome`() {
        val (state, commands) = run(Event.DidFinishLaunch(hasCompletedWelcome = false))
        assertEquals(Phase.WELCOME, state.phase)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `later launches start main`() {
        val (state, commands) = run(Event.DidFinishLaunch(hasCompletedWelcome = true))
        assertEquals(Phase.MAIN, state.phase)
        assertEquals(listOf<Command>(Command.WillStartMain), commands)
    }

    @Test
    fun `finishing welcome saves it first and starts main once it is saved`() {
        val (saving, save) = run(Event.DidFinishLaunch(hasCompletedWelcome = false), Event.DidFinishWelcome)
        assertEquals(Phase.WELCOME, saving.phase)
        assertEquals(listOf<Command>(Command.WillRememberWelcomeCompleted), save)

        val (again, none) = run(Event.DidFinishWelcome, from = saving)
        assertEquals(saving, again)
        assertTrue(none.isEmpty())

        val (state, commands) = run(Event.DidRememberWelcomeCompleted, from = saving)
        assertEquals(Phase.MAIN, state.phase)
        assertEquals(listOf<Command>(Command.WillStartMain), commands)
    }

    @Test
    fun `events that do not fit the phase are ignored`() {
        val cases = listOf(
            Phase.WELCOME to Event.DidFinishLaunch(hasCompletedWelcome = true),
            Phase.MAIN to Event.DidFinishLaunch(hasCompletedWelcome = false),
            Phase.LAUNCH to Event.DidFinishWelcome,
            Phase.MAIN to Event.DidFinishWelcome,
        )
        for ((phase, event) in cases) {
            val (state, commands) = run(event, from = State(phase))
            assertEquals(phase, state.phase, "$event in $phase")
            assertTrue(commands.isEmpty(), "$event in $phase")
        }
    }

    @Test
    fun `a notification tapped before main opens once main starts`() {
        val (waiting, none) = run(Event.DidOpenItemNotification("milk"))
        assertEquals("milk", waiting.pendingItemId)
        assertTrue(none.isEmpty())

        val (state, commands) = run(Event.DidFinishLaunch(hasCompletedWelcome = true), from = waiting)
        assertNull(state.pendingItemId)
        assertEquals(listOf(Command.WillStartMain, Command.WillShowItem("milk")), commands)
    }

    @Test
    fun `a notification tapped during welcome opens once it is finished`() {
        val (state, commands) = run(
            Event.DidOpenItemNotification("milk"), Event.DidFinishWelcome, Event.DidRememberWelcomeCompleted, from = State(Phase.WELCOME),
        )
        assertNull(state.pendingItemId)
        assertEquals(listOf(Command.WillStartMain, Command.WillShowItem("milk")), commands)
    }

    @Test
    fun `a notification tapped in main opens at once`() {
        val (_, commands) = run(Event.DidOpenItemNotification("milk"), from = State(Phase.MAIN))
        assertEquals(listOf<Command>(Command.WillShowItem("milk")), commands)
    }

    @Test
    fun `becoming active refreshes in every phase`() {
        for (phase in Phase.entries) {
            assertEquals(listOf<Command>(Command.WillRefreshServices), run(Event.DidBecomeActive, from = State(phase)).second)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppCoordinatorTest {

    @Test
    fun `first launch goes through welcome then remembers it`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettings()
        val dependencies = testDependencies(settings = settings)
        val app = AppCoordinator(dependencies, backgroundScope)
        app.finishLaunch().join()
        assertEquals(Phase.WELCOME, app.state.phase)
        assertNull(app.main)

        app.eventLoop(Event.DidFinishWelcome).join()
        assertEquals(Phase.MAIN, app.state.phase)
        assertNotNull(app.main)
        assertTrue(settings.current().hasCompletedWelcome)

        val relaunch = AppCoordinator(dependencies, backgroundScope)
        relaunch.finishLaunch().join()
        assertEquals(Phase.MAIN, relaunch.state.phase)
    }

    @Test
    fun `a notification tapped on the splash opens the item on the fridge tab`() = runTest(UnconfinedTestDispatcher()) {
        val app = AppCoordinator(testDependencies(settings = FakeSettings(SettingsSnapshot(hasCompletedWelcome = true))), backgroundScope)
        app.eventLoop(Event.DidOpenItemNotification("milk"))
        app.finishLaunch().join()

        val main = assertNotNull(app.main)
        assertEquals(AppTab.FRIDGE, main.state.selectedTab)
        assertEquals(listOf(InventoryRoute.Fridge, InventoryRoute.ItemDetail("milk")), main.inventory.state.backStack)
    }

    @Test
    fun `an ignored event runs nothing and is logged as ignored`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeSettings()
        val app = AppCoordinator(testDependencies(settings = settings), backgroundScope)
        app.eventLoop(Event.DidFinishWelcome).join() // still on the splash
        assertEquals(Phase.LAUNCH, app.state.phase)
        assertFalse(settings.current().hasCompletedWelcome)
        assertTrue(app.history.last().wasIgnored)
    }

    @Test
    fun `becoming active resyncs reminders`() = runTest(UnconfinedTestDispatcher()) {
        val scheduler = FakeScheduler()
        val app = AppCoordinator(testDependencies(scheduler = scheduler), backgroundScope)
        app.eventLoop(Event.DidBecomeActive).join()
        assertEquals(1, scheduler.calls)
    }
}
