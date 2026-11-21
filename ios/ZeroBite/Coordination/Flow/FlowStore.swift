//
//  FlowStore.swift
//  ZeroBite
//
//  Runs one FlowMachine: holds its state, applies events through the pure reducer, and
//  runs the resulting effects. Async effects are cancellable tasks whose results come back
//  in as events on the main actor.
//

import Foundation
import Observation

@MainActor
@Observable
final class FlowStore<Machine: FlowMachine> {

    /// The single source of truth. Changes only through send(_:).
    private(set) var state: Machine.State

    @ObservationIgnored private let handle: @MainActor (Machine.Effect) -> EffectTask<Machine.Event>
    @ObservationIgnored private var running: [AnyHashable: (token: UUID, task: Task<Void, Never>)] = [:]

    init(_ initial: Machine.State, effects: @escaping @MainActor (Machine.Effect) -> EffectTask<Machine.Event>) {
        state = initial
        handle = effects
    }

    /// Applies an event: the transition first, then its effects.
    ///
    /// Returns a task that finishes when every async effect started here, and the events
    /// those effects send back, have finished. The app ignores it; tests await it.
    @discardableResult
    func send(_ event: Machine.Event) -> Task<Void, Never> {
        var next = state
        let effects = Machine.reduce(&next, event)
        if next != state { state = next }

        let started = effects.compactMap { start(handle($0)) }
        return Task { for task in started { await task.value } }
    }

    // MARK: - Effects

    private func start(_ effect: EffectTask<Machine.Event>) -> Task<Void, Never>? {
        switch effect.kind {
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
            let task = Task { [weak self] in
                let event = await work()
                guard let self else { return }
                if let id, running[id]?.token == token { running[id] = nil }
                // Cancelled work never reports back, even if it finished anyway.
                guard !Task.isCancelled, let event else { return }
                await send(event).value
            }
            if let id { running[id] = (token, task) }
            return task
        }
    }

    private func cancel(_ id: AnyHashable) {
        running.removeValue(forKey: id)?.task.cancel()
    }
}
