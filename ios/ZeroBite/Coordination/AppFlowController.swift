//
//  AppFlowController.swift
//  ZeroBite
//
//  The app-flow finite state machine. `phase` is the single source of truth for
//  which root screen is shown, and changes only through send(_:), which applies
//  the pure AppFlowPhase.reduce and then runs side effects. Effects live in the
//  controller, never in the reducer.
//

import Foundation
import Observation

/// Which root screen is shown.
enum AppFlowPhase: Equatable {
    case launch   // splash
    case welcome  // first-run onboarding
    case main     // tabs

    /// Inputs that can move the app between phases.
    enum Event: Equatable {
        case launchFinished(hasCompletedWelcome: Bool)  // splash done
        case welcomeFinished                            // onboarding done, with or without notifications
    }

    /// Pure, total transition: no side effects, so it is deterministic and unit-testable.
    /// An event that does not apply to the current phase leaves it unchanged.
    static func reduce(_ state: AppFlowPhase, on event: Event) -> AppFlowPhase {
        switch event {
        case .launchFinished(let hasCompletedWelcome):
            guard state == .launch else { return state }
            return hasCompletedWelcome ? .main : .welcome
        case .welcomeFinished:
            return state == .welcome ? .main : state
        }
    }
}

@MainActor
@Observable
final class AppFlowController {

    private(set) var phase: AppFlowPhase = .launch
    private let settings: AppSettings

    init(settings: AppSettings) {
        self.settings = settings
    }

    /// Apply an event: pure transition first, then the side effects of that transition.
    /// Effects run only when the phase actually changed, so an ignored event does nothing.
    func send(_ event: AppFlowPhase.Event) {
        let previous = phase
        phase = AppFlowPhase.reduce(previous, on: event)
        guard phase != previous else { return }
        runEffects(for: event)
    }

    /// Call when the splash is done. Reads the stored flag so the reducer stays pure.
    func finishLaunch() {
        send(.launchFinished(hasCompletedWelcome: settings.hasCompletedWelcome))
    }

    // MARK: - Effects

    private func runEffects(for event: AppFlowPhase.Event) {
        switch event {
        case .welcomeFinished:
            settings.hasCompletedWelcome = true
        case .launchFinished:
            break
        }
    }
}
