//
//  AppFlowController.swift
//  ZeroBite
//
//  The app-flow finite state machine: which root screen is shown. AppFlow is the pure
//  machine; AppFlowController runs it and owns its one side effect (remembering that the
//  welcome flow was completed).
//

import Foundation
import Observation

/// Which root screen is shown.
enum AppFlowPhase: Equatable {
    case launch   // splash
    case welcome  // first-run onboarding
    case main     // tabs
}

enum AppFlow: FlowMachine {

    /// Inputs that can move the app between phases.
    enum Event: Equatable, Sendable {
        case launchFinished(hasCompletedWelcome: Bool)  // splash done
        case welcomeFinished                            // onboarding done, with or without notifications
    }

    enum Effect: Equatable {
        case rememberWelcomeCompleted
    }

    static func reduce(_ state: inout AppFlowPhase, _ event: Event) -> [Effect] {
        switch (state, event) {
        case (.launch, .launchFinished(let hasCompletedWelcome)):
            state = hasCompletedWelcome ? .main : .welcome
            return []
        case (.welcome, .welcomeFinished):
            state = .main
            return [.rememberWelcomeCompleted]
        default:
            return []
        }
    }
}

@MainActor
@Observable
final class AppFlowController {

    private let store: FlowStore<AppFlow>
    private let settings: AppSettings

    init(settings: AppSettings) {
        self.settings = settings
        store = FlowStore(.launch) { effect in
            switch effect {
            case .rememberWelcomeCompleted:
                .perform { settings.hasCompletedWelcome = true }
            }
        }
    }

    var phase: AppFlowPhase { store.state }

    @discardableResult
    func send(_ event: AppFlow.Event) -> Task<Void, Never> {
        store.send(event)
    }

    /// Call when the splash is done. Reads the stored flag so the reducer stays pure.
    func finishLaunch() {
        send(.launchFinished(hasCompletedWelcome: settings.hasCompletedWelcome))
    }
}
