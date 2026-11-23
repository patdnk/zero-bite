//
//  EventLoop.swift
//  ZeroBite
//
//  The engine behind every coordinator's eventLoop(event:): holds the state, runs the pure
//  stateMachine, hands the commands to the coordinator, and keeps async commands as keyed,
//  cancellable tasks whose results come back in as events on the main actor.
//
//  Every transition goes through send(_:to:), so it is the one place to log and record.
//  Debug builds log each transition (category "flow") and keep the last few in `history`,
//  which replays through the coordinator's static replay(_:from:).
//

import Foundation
import Observation
import os

/// One pass through a state machine, as recorded in debug builds.
struct Transition<State: Equatable, Event, Command> {
    let event: Event
    let from: State
    let to: State
    let commands: [Command]

    /// Nothing changed and nothing was asked for: the event didn't fit the state.
    var wasIgnored: Bool { commands.isEmpty && from == to }
}

@MainActor
@Observable
final class EventLoop<State: Equatable, Event: Sendable, Command> {

    /// The coordinator's single source of truth. Changes only through send(_:to:).
    private(set) var state: State

    /// The most recent transitions, oldest first. Always empty in release builds.
    @ObservationIgnored private(set) var history: [Transition<State, Event, Command>] = []

    @ObservationIgnored private var running: [AnyHashable: (token: UUID, task: Task<Void, Never>)] = [:]
    @ObservationIgnored private let historyLimit = 100

    private static var log: Logger { Logger(subsystem: "com.mazesys.zerobite", category: "flow") }

    init(_ initial: State) {
        state = initial
    }

    func send<C: EventDrivenCoordinator>(_ event: Event, to coordinator: C) -> Task<Void, Never>
    where C.State == State, C.Event == Event, C.Command == Command {
        let from = state
        var next = state
        let commands = C.stateMachine(&next, event)
        record(Transition(event: event, from: from, to: next, commands: commands), in: C.self)
        if next != state { state = next }

        let started = commands.compactMap { start(coordinator.execute($0), reportingTo: coordinator) }
        return Task { for task in started { await task.value } }
    }

    // MARK: - Commands

    private func start<C: EventDrivenCoordinator>(_ command: CommandTask<Event>, reportingTo coordinator: C) -> Task<Void, Never>?
    where C.Event == Event {
        switch command.kind {
        case .none:
            return nil
        case .perform(let action):
            action()
            return nil
        case .cancel(let id):
            cancel(id)
            return nil
        case .run(let id, let work):
            if let id { cancel(id) }
            let token = UUID()
            let task = Task { [weak self, weak coordinator] in
                let event = await work()
                if let id, let self, running[id]?.token == token { running[id] = nil }
                // Cancelled work never reports back, even if it finished anyway.
                guard !Task.isCancelled, let event, let coordinator else { return }
                await coordinator.eventLoop(event: event).value
            }
            if let id { running[id] = (token, task) }
            return task
        }
    }

    private func cancel(_ id: AnyHashable) {
        running.removeValue(forKey: id)?.task.cancel()
    }

    // MARK: - Debugging

    private func record<C>(_ transition: Transition<State, Event, Command>, in coordinator: C.Type) {
        #if DEBUG
        history.append(transition)
        if history.count > historyLimit { history.removeFirst(history.count - historyLimit) }

        let name = String(describing: coordinator)
        if transition.wasIgnored {
            Self.log.debug("\(name, privacy: .public) ignored \(String(describing: transition.event)) in \(String(describing: transition.from))")
        } else {
            let commands = transition.commands.map { String(describing: $0) }.joined(separator: ", ")
            Self.log.debug("\(name, privacy: .public) \(String(describing: transition.event)) → [\(commands)] state: \(String(describing: transition.to))")
        }
        #endif
    }
}
