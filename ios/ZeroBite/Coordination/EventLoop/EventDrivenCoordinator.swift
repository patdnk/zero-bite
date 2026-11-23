//
//  EventDrivenCoordinator.swift
//  ZeroBite
//
//  The shape every coordinator takes, as in the TRG apps: events come in through
//  eventLoop(event:), a static stateMachine decides what happens, and the coordinator
//  carries out the resulting "will…" commands (start a child, tell the parent, run work).
//
//  Two differences from the TRG version, both for async/await:
//  - The state is an explicit value, so a transition can depend on where the flow is and an
//    event that doesn't fit is ignored (and logged in debug builds) instead of crashing.
//  - stateMachine is pure and synchronous. Async work is a command that runs as a cancellable
//    task and reports back with another event, so nothing changes the state mid-transition
//    (actor reentrancy) and a late result is just an event the state machine can ignore.
//
//  Children report to their parent with parent?.eventLoop(event: .didFinish…).
//

import Foundation

@MainActor
protocol EventDrivenCoordinator: AnyObject {
    associatedtype State: Equatable
    /// What happened: user input ("didTap…"), a child or parent reporting ("didFinish…",
    /// "didRequest…"), or async work finishing. Anything non-deterministic (now, data read
    /// from disk) travels in the event so stateMachine stays pure and replayable.
    associatedtype Event: Sendable
    /// What to do next ("will…"). Plain values: stateMachine returns them, execute does them.
    associatedtype Command

    /// Holds the state and runs the async commands. Created once, in init.
    var loop: EventLoop<State, Event, Command> { get }

    /// Pure and total: no side effects, no clock, no I/O. An event that doesn't apply to
    /// the current state leaves it unchanged and returns no commands. Nonisolated, so tests
    /// and replays can call it from anywhere.
    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command]

    /// Carries out one command.
    func execute(_ command: Command) -> CommandTask<Event>
}

extension EventDrivenCoordinator {

    var state: State { loop.state }

    /// Applies an event: the transition first, then its commands.
    ///
    /// Returns a task that finishes when every async command started here, and the events
    /// those commands send back, have finished. The app ignores it; tests await it.
    @discardableResult
    func eventLoop(event: Event) -> Task<Void, Never> {
        loop.send(event, to: self)
    }

    /// Runs events through stateMachine alone, carrying nothing out. Replays a recorded
    /// session (EventLoop.history) or a bug report's event list deterministically.
    nonisolated static func replay(_ events: [Event], from initial: State) -> (state: State, commands: [[Command]]) {
        var state = initial
        let commands = events.map { stateMachine(&state, $0) }
        return (state, commands)
    }
}

/// What a coordinator turns one command into.
struct CommandTask<Event: Sendable> {

    enum Kind {
        case none
        /// Synchronous work on the main actor: start or stop a child, tell the parent, save.
        case perform(@MainActor () -> Void)
        /// Async work. Its event, if any, is sent back into the loop. Starting work with an id
        /// cancels any work still running under the same id.
        case run(id: AnyHashable?, work: @Sendable () async -> Event?)
        case cancel(id: AnyHashable)
    }

    let kind: Kind

    static var none: Self { .init(kind: .none) }

    static func perform(_ action: @escaping @MainActor () -> Void) -> Self {
        .init(kind: .perform(action))
    }

    static func run(id: AnyHashable? = nil, _ work: @escaping @Sendable () async -> Event?) -> Self {
        .init(kind: .run(id: id, work: work))
    }

    static func cancel(id: AnyHashable) -> Self {
        .init(kind: .cancel(id: id))
    }
}
