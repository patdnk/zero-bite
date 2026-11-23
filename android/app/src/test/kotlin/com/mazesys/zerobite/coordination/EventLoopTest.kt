package com.mazesys.zerobite.coordination

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tiny coordinator for exercising the event loop: each DidTapStart runs keyed work that reports
 * back. Work for 1 never finishes on its own; it only ends when cancelled. Work for 3 takes a
 * moment. Anything else finishes at once.
 */
private class CounterCoordinator(scope: CoroutineScope, private val onNotify: () -> Unit = {}) :
    EventDrivenCoordinator<CounterCoordinator.State, CounterCoordinator.Event, CounterCoordinator.Command>(State(), CounterCoordinator, scope) {

    data class State(val finished: List<Int> = emptyList(), val notified: Int = 0)

    sealed interface Event {
        data class DidTapStart(val n: Int) : Event
        data class DidFinish(val n: Int) : Event
        data object DidTapCancel : Event
        data object DidTapNotify : Event
    }

    sealed interface Command {
        data class WillWork(val n: Int) : Command
        data object WillCancelWork : Command
        data object WillNotifyParent : Command
    }

    companion object : StateMachine<State, Event, Command> {
        override fun stateMachine(state: State, event: Event): Step<State, Command> = when (event) {
            is Event.DidTapStart -> Step(state, listOf(Command.WillWork(event.n)))
            is Event.DidFinish -> Step(state.copy(finished = state.finished + event.n))
            Event.DidTapCancel -> Step(state, listOf(Command.WillCancelWork))
            Event.DidTapNotify -> Step(state.copy(notified = state.notified + 1), listOf(Command.WillNotifyParent))
        }
    }

    override fun execute(command: Command): CommandTask<Event> = when (command) {
        is Command.WillWork -> CommandTask.Run(id = "work") {
            when (command.n) {
                1 -> delay(60_000)
                3 -> delay(2_000)
            }
            Event.DidFinish(command.n)
        }
        Command.WillCancelWork -> CommandTask.Cancel("work")
        Command.WillNotifyParent -> CommandTask.Perform(onNotify)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EventLoopTest {

    @Test
    fun `async work reports back as an event`() = runTest(UnconfinedTestDispatcher()) {
        val counter = CounterCoordinator(backgroundScope)
        counter.eventLoop(CounterCoordinator.Event.DidTapStart(2)).join()
        assertEquals(listOf(2), counter.state.finished)
    }

    @Test
    fun `starting work with the same id cancels the old work`() = runTest(UnconfinedTestDispatcher()) {
        val counter = CounterCoordinator(backgroundScope)
        val slow = counter.eventLoop(CounterCoordinator.Event.DidTapStart(1))
        val fast = counter.eventLoop(CounterCoordinator.Event.DidTapStart(2))
        slow.join()
        fast.join()
        assertEquals(listOf(2), counter.state.finished)
    }

    @Test
    fun `cancelled work never reports back`() = runTest(UnconfinedTestDispatcher()) {
        val counter = CounterCoordinator(backgroundScope)
        val slow = counter.eventLoop(CounterCoordinator.Event.DidTapStart(1))
        counter.eventLoop(CounterCoordinator.Event.DidTapCancel)
        slow.join()
        assertTrue(counter.state.finished.isEmpty())
    }

    /** The replaced work finishing must not drop the new work's entry, or it can't be cancelled. */
    @Test
    fun `replaced work finishing leaves the new work cancellable`() = runTest(UnconfinedTestDispatcher()) {
        val counter = CounterCoordinator(backgroundScope)
        val replaced = counter.eventLoop(CounterCoordinator.Event.DidTapStart(1))
        val current = counter.eventLoop(CounterCoordinator.Event.DidTapStart(3))
        replaced.join()
        counter.eventLoop(CounterCoordinator.Event.DidTapCancel)
        current.join()
        assertTrue(counter.state.finished.isEmpty())
    }

    @Test
    fun `perform runs synchronously on the event`() = runTest(UnconfinedTestDispatcher()) {
        var notified = 0
        val counter = CounterCoordinator(backgroundScope) { notified++ }
        counter.eventLoop(CounterCoordinator.Event.DidTapNotify)
        assertEquals(1, notified)
        assertEquals(1, counter.state.notified)
    }

    /** The history includes events async work sent back, and replays to the same state. */
    @Test
    fun `history replays to the same state`() = runTest(UnconfinedTestDispatcher()) {
        val counter = CounterCoordinator(backgroundScope)
        counter.eventLoop(CounterCoordinator.Event.DidTapStart(2)).join()
        counter.eventLoop(CounterCoordinator.Event.DidTapNotify)
        counter.eventLoop(CounterCoordinator.Event.DidTapStart(4)).join()

        val history = counter.history
        assertEquals(
            listOf(
                CounterCoordinator.Event.DidTapStart(2), CounterCoordinator.Event.DidFinish(2), CounterCoordinator.Event.DidTapNotify,
                CounterCoordinator.Event.DidTapStart(4), CounterCoordinator.Event.DidFinish(4),
            ),
            history.map { it.event },
        )
        val replayed = CounterCoordinator.replay(history.map { it.event }, from = CounterCoordinator.State())
        assertEquals(counter.state, replayed.state)
        assertEquals(history.map { it.commands }, replayed.commands)
    }

    @Test
    fun `an event that changes nothing is recorded as ignored`() {
        val ignored = Transition(CounterCoordinator.Event.DidTapCancel, CounterCoordinator.State(), CounterCoordinator.State(), emptyList<CounterCoordinator.Command>())
        assertTrue(ignored.wasIgnored)
    }
}
