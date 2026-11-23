//
//  EventLoopTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

/// A tiny coordinator for exercising EventLoop: each `didTapStart` runs keyed work that
/// reports back. Work for 1 never finishes on its own; it only ends when cancelled. Work for 3
/// takes a moment. Anything else finishes at once.
@MainActor
private final class CounterCoordinator: EventDrivenCoordinator {

    struct State: Equatable {
        var finished: [Int] = []
        var notified = 0
    }

    enum Event: Equatable, Sendable {
        case didTapStart(Int)
        case didFinish(Int)
        case didTapCancel
        case didTapNotify
    }

    enum Command: Equatable {
        case willWork(Int)
        case willCancelWork
        case willNotifyParent
    }

    let loop = EventLoop<State, Event, Command>(State())
    private let onNotify: @MainActor () -> Void

    init(onNotify: @escaping @MainActor () -> Void = {}) {
        self.onNotify = onNotify
    }

    static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch event {
        case .didTapStart(let n): return [.willWork(n)]
        case .didFinish(let n): state.finished.append(n); return []
        case .didTapCancel: return [.willCancelWork]
        case .didTapNotify: state.notified += 1; return [.willNotifyParent]
        }
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willWork(let n):
            .run(id: "work") {
                switch n {
                case 1: try? await Task.sleep(for: .seconds(60))
                case 3: try? await Task.sleep(for: .seconds(2))
                default: break
                }
                return Task.isCancelled ? nil : .didFinish(n)
            }
        case .willCancelWork:
            .cancel(id: "work")
        case .willNotifyParent:
            .perform(onNotify)
        }
    }
}

/// Counts calls from @MainActor closures, which can't capture a local `var`.
@MainActor
final class CallCount {
    private(set) var value = 0
    func hit() { value += 1 }
}

@MainActor
struct EventLoopTests {

    @Test func asyncWorkReportsBackAsAnEvent() async {
        let coordinator = CounterCoordinator()
        await coordinator.eventLoop(event: .didTapStart(2)).value
        #expect(coordinator.state.finished == [2])
    }

    @Test func startingWorkWithTheSameIDCancelsTheOldWork() async {
        let coordinator = CounterCoordinator()
        let slow = coordinator.eventLoop(event: .didTapStart(1))
        let fast = coordinator.eventLoop(event: .didTapStart(2))
        await slow.value
        await fast.value
        #expect(coordinator.state.finished == [2])
    }

    @Test func cancelledWorkNeverReportsBack() async {
        let coordinator = CounterCoordinator()
        let slow = coordinator.eventLoop(event: .didTapStart(1))
        coordinator.eventLoop(event: .didTapCancel)
        await slow.value
        #expect(coordinator.state.finished.isEmpty)
    }

    /// The replaced work finishing must not drop the new work's entry, or it can't be cancelled.
    @Test func replacedWorkFinishingLeavesTheNewWorkCancellable() async {
        let coordinator = CounterCoordinator()
        let replaced = coordinator.eventLoop(event: .didTapStart(1))
        let current = coordinator.eventLoop(event: .didTapStart(3))
        await replaced.value
        coordinator.eventLoop(event: .didTapCancel)
        await current.value
        #expect(coordinator.state.finished.isEmpty)
    }

    @Test func performRunsSynchronouslyOnTheEvent() {
        let notified = CallCount()
        let coordinator = CounterCoordinator(onNotify: { notified.hit() })
        coordinator.eventLoop(event: .didTapNotify)
        #expect(notified.value == 1)
        #expect(coordinator.state.notified == 1)
    }

    /// Debug builds record every transition, including the events async work sent back, and
    /// the recording replays through the pure state machine to the same state.
    @Test func historyReplaysToTheSameState() async {
        let coordinator = CounterCoordinator()
        await coordinator.eventLoop(event: .didTapStart(2)).value
        coordinator.eventLoop(event: .didTapNotify)
        await coordinator.eventLoop(event: .didTapStart(4)).value

        let history = coordinator.loop.history
        #expect(history.map(\.event) == [.didTapStart(2), .didFinish(2), .didTapNotify, .didTapStart(4), .didFinish(4)])

        let replayed = CounterCoordinator.replay(history.map(\.event), from: CounterCoordinator.State())
        #expect(replayed.state == coordinator.state)
        #expect(replayed.commands == history.map(\.commands))
    }
}
