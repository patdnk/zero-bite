//
//  FlowStoreTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

/// A tiny machine for exercising FlowStore: each `start` runs keyed work that reports back.
private enum Counter: FlowMachine {
    struct State: Equatable {
        var finished: [Int] = []
        var notified = 0
    }

    enum Event: Equatable, Sendable {
        case start(Int)
        case finished(Int)
        case cancel
        case notify
    }

    enum Effect: Equatable {
        case work(Int)
        case cancelWork
        case notifyParent
    }

    static func reduce(_ state: inout State, _ event: Event) -> [Effect] {
        switch event {
        case .start(let n): return [.work(n)]
        case .finished(let n): state.finished.append(n); return []
        case .cancel: return [.cancelWork]
        case .notify: state.notified += 1; return [.notifyParent]
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
struct FlowStoreTests {

    /// Work for 1 never finishes on its own; it only ends when cancelled. Work for 3 takes a
    /// moment. Anything else finishes at once.
    private func makeStore(onNotify: @escaping @MainActor () -> Void = {}) -> FlowStore<Counter> {
        FlowStore(Counter.State()) { effect in
            switch effect {
            case .work(let n):
                .run(id: "work") {
                    switch n {
                    case 1: try? await Task.sleep(for: .seconds(60))
                    case 3: try? await Task.sleep(for: .seconds(2))
                    default: break
                    }
                    return Task.isCancelled ? nil : .finished(n)
                }
            case .cancelWork:
                .cancel(id: "work")
            case .notifyParent:
                .perform(onNotify)
            }
        }
    }

    @Test func asyncWorkReportsBackAsAnEvent() async {
        let store = makeStore()
        await store.send(.start(2)).value
        #expect(store.state.finished == [2])
    }

    @Test func startingWorkWithTheSameIDCancelsTheOldWork() async {
        let store = makeStore()
        let slow = store.send(.start(1))
        let fast = store.send(.start(2))
        await slow.value
        await fast.value
        #expect(store.state.finished == [2])
    }

    @Test func cancelledWorkNeverReportsBack() async {
        let store = makeStore()
        let slow = store.send(.start(1))
        store.send(.cancel)
        await slow.value
        #expect(store.state.finished.isEmpty)
    }

    /// The replaced work finishing must not drop the new work's entry, or it can't be cancelled.
    @Test func replacedWorkFinishingLeavesTheNewWorkCancellable() async {
        let store = makeStore()
        let replaced = store.send(.start(1))
        let current = store.send(.start(3))
        await replaced.value
        store.send(.cancel)
        await current.value
        #expect(store.state.finished.isEmpty)
    }

    @Test func performRunsSynchronouslyOnSend() {
        let notified = CallCount()
        let store = makeStore(onNotify: { notified.hit() })
        store.send(.notify)
        #expect(notified.value == 1)
        #expect(store.state.notified == 1)
    }
}
