//
//  FlowMachine.swift
//  ZeroBite
//
//  The shape every coordination module's finite state machine takes:
//  a synchronous, pure reducer that turns (state, event) into the next state plus a list of
//  effects. Effects are plain values; FlowStore turns each one into work through the
//  module's effect handler, and async work reports back as another event.
//
//  The reducer never awaits. Coordinators are @MainActor, and an await inside a transition
//  would let other events change the state mid-transition (actor reentrancy). A late async
//  result is just an event the reducer can ignore when it no longer fits the state.
//

import Foundation

protocol FlowMachine {
    associatedtype State: Equatable
    associatedtype Event: Sendable
    associatedtype Effect: Equatable

    /// Pure and total: no side effects, no clock, no I/O. Events that don't apply to the
    /// current state leave it unchanged and return no effects.
    static func reduce(_ state: inout State, _ event: Event) -> [Effect]
}

/// What a module's effect handler turns one effect into.
struct EffectTask<Event: Sendable> {

    enum Kind {
        case none
        /// Synchronous work on the main actor (tell the parent, save a setting).
        case perform(@MainActor () -> Void)
        /// Async work. Its event, if any, is sent back to the store. Starting work with an
        /// id cancels any work still running under the same id.
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
