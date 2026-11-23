package com.mazesys.zerobite.coordination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/*
 * The shape every coordinator takes, as in the TRG apps and the iOS app: events come in through
 * eventLoop(event), a pure stateMachine decides what happens, and the coordinator carries out the
 * resulting "will…" commands (start a child, tell the parent, run work).
 *
 * Unlike the TRG version:
 * - The state is an explicit value, so a transition can depend on where the flow is, and an
 *   event that doesn't fit is ignored (and logged) instead of crashing.
 * - stateMachine is pure and synchronous. Async work is a command that runs as a cancellable
 *   coroutine and reports back with another event, so nothing changes the state mid-transition
 *   and a late result is just an event the state machine can ignore.
 *
 * Children report to their parent with parent.eventLoop(Event.DidFinish…).
 */

/** One transition's result: the next state and what to do about it. */
data class Step<out S, out C>(val state: S, val commands: List<C> = emptyList())

/**
 * A coordinator's pure transition function. Each coordinator's companion object implements it, so
 * `ScanCoordinator.stateMachine(state, event)` and `ScanCoordinator.replay(events, from)` read like
 * the TRG `static func stateMachine`.
 */
fun interface StateMachine<S, E, C> {
    /**
     * Pure and total: no side effects, no clock, no I/O. An event that doesn't apply to the
     * current state returns it unchanged with no commands. Anything non-deterministic (today's
     * date, a value read from storage) travels in the event.
     */
    fun stateMachine(state: S, event: E): Step<S, C>
}

/** Runs events through the state machine alone, carrying nothing out. */
fun <S, E, C> StateMachine<S, E, C>.replay(events: List<E>, from: S): Replay<S, C> {
    var state = from
    val commands = events.map { event ->
        val step = stateMachine(state, event)
        state = step.state
        step.commands
    }
    return Replay(state, commands)
}

data class Replay<out S, out C>(val state: S, val commands: List<List<C>>)

/** One pass through a state machine, kept in the coordinator's history. */
data class Transition<out S, out E, out C>(val event: E, val from: S, val to: S, val commands: List<C>) {
    /** Nothing changed and nothing was asked for: the event didn't fit the state. */
    val wasIgnored: Boolean get() = commands.isEmpty() && from == to
}

/** What a coordinator turns one command into. */
sealed interface CommandTask<out E> {
    data object None : CommandTask<Nothing>

    /** Synchronous work on the main thread: start or stop a child, tell the parent, save. */
    class Perform(val action: () -> Unit) : CommandTask<Nothing>

    /**
     * Async work. Its event, if any, is sent back into the loop. Starting work with an id cancels
     * any work still running under the same id. Work catches its own failures and reports them
     * as events.
     */
    class Run<out E>(val id: Any? = null, val work: suspend () -> E?) : CommandTask<E>

    class Cancel(val id: Any) : CommandTask<Nothing>
}

/** Debug-build logging of every transition (tag "flow"). Set once by the application. */
object FlowLog {
    @Volatile var sink: ((String) -> Unit)? = null
}

/**
 * Holds a coordinator's state and runs its commands. Call eventLoop only from the main thread;
 * [scope] should dispatch on the main thread too (viewModelScope does).
 */
abstract class EventDrivenCoordinator<S, E, C>(
    initial: S,
    private val machine: StateMachine<S, E, C>,
    protected val scope: CoroutineScope,
) {
    /** The single source of truth. Compose state, so screens that read it recompose. */
    var state: S by mutableStateOf(initial)
        private set

    private val recent = ArrayDeque<Transition<S, E, C>>()

    /** The most recent transitions, oldest first, including events that async work sent back. */
    val history: List<Transition<S, E, C>> get() = recent.toList()

    private val running = mutableMapOf<Any, Job>()

    /** Carries out one command. */
    protected abstract fun execute(command: C): CommandTask<E>

    /**
     * Applies an event: the transition first, then its commands in order. Async work starts only
     * after every command of the step has run, so work that finishes without suspending (on
     * Main.immediate it runs inline) can't send its event back in the middle of the step.
     *
     * Returns a job that finishes when this coordinator's async commands started here, and the
     * events they send back, have finished. Work a child starts runs in the child's own loop.
     * The app ignores it; tests join it.
     */
    fun eventLoop(event: E): Job {
        val from = state
        val step = machine.stateMachine(from, event)
        record(Transition(event, from, step.state, step.commands))
        if (step.state != from) state = step.state

        val started = step.commands.mapNotNull { start(execute(it)) }
        started.forEach(Job::start)
        return if (started.isEmpty()) finished() else scope.launch { started.joinAll() }
    }

    private fun start(task: CommandTask<E>): Job? = when (task) {
        CommandTask.None -> null
        is CommandTask.Perform -> {
            task.action()
            null
        }
        is CommandTask.Cancel -> {
            cancel(task.id)
            null
        }
        is CommandTask.Run -> {
            task.id?.let(::cancel)
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                val next = try {
                    task.work()
                } finally {
                    if (task.id != null && running[task.id] === job) running.remove(task.id)
                }
                // Cancelled work never reports back, even if it finished anyway.
                ensureActive()
                if (next != null) eventLoop(next).join()
            }
            task.id?.let { running[it] = job }
            job // started by eventLoop once the whole step has run
        }
    }

    private fun cancel(id: Any) {
        running.remove(id)?.cancel()
    }

    private fun record(transition: Transition<S, E, C>) {
        recent.addLast(transition)
        while (recent.size > HISTORY_LIMIT) recent.removeFirst()

        val sink = FlowLog.sink ?: return
        val name = this::class.simpleName
        if (transition.wasIgnored) {
            sink("$name ignored ${transition.event} in ${transition.from}")
        } else {
            sink("$name ${transition.event} → ${transition.commands} state: ${transition.to}")
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 100

        fun finished(): CompletableJob = Job().apply { complete() }
    }
}
