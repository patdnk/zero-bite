//
//  AppCoordinator.swift
//  ZeroBite
//
//  The root of the coordinator tree: which root screen is shown (splash → welcome → main),
//  starting MainCoordinator, holding a notification tap until main is on screen, and
//  refreshing services when the app becomes active.
//

import Foundation
import Observation

@MainActor
@Observable
final class AppCoordinator: EventDrivenCoordinator {

    let dependencies: AppDependencies
    let loop = EventLoop<State, Event, Command>(State())

    /// Started by willStartMain; nil until the app reaches the main phase.
    private(set) var main: MainCoordinator?

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    /// The splash is done. Reads the stored flag here so stateMachine stays pure.
    func finishLaunch() {
        eventLoop(event: .didFinishLaunch(hasCompletedWelcome: dependencies.settings.hasCompletedWelcome))
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willRememberWelcomeCompleted:
            return .perform { [dependencies] in dependencies.settings.hasCompletedWelcome = true }

        case .willStartMain:
            return .perform { [weak self] in
                guard let self, main == nil else { return }
                main = MainCoordinator(parent: self, dependencies: dependencies)
            }

        case .willShowItem(let id):
            return .perform { [weak self] in self?.main?.eventLoop(event: .didRequestItem(id)) }

        case .willRefreshServices:
            let dependencies = dependencies
            return .run {
                await dependencies.notifications.refreshAuthorizationStatus()
                await dependencies.syncNotifications()
                await dependencies.refreshRecalls()
                return nil
            }
        }
    }
}

// MARK: - State

extension AppCoordinator {

    /// Which root screen is shown.
    enum Phase: Equatable {
        case launch   // splash
        case welcome  // first-run onboarding
        case main     // tabs
    }

    struct State: Equatable {
        var phase: Phase = .launch
        /// A notification was tapped before the tabs were on screen; open it once they are.
        var pendingItemID: UUID?
    }
}

// MARK: - Events and commands

extension AppCoordinator {

    enum Event: Equatable, Sendable {
        case didFinishLaunch(hasCompletedWelcome: Bool)
        /// Onboarding done, with or without notifications.
        case didFinishWelcome
        case didOpenItemNotification(UUID)
        case didBecomeActive
    }

    enum Command: Equatable {
        case willRememberWelcomeCompleted
        case willStartMain
        case willShowItem(UUID)
        case willRefreshServices
    }
}

// MARK: - State machine

extension AppCoordinator {

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch (state.phase, event) {
        case (.launch, .didFinishLaunch(let hasCompletedWelcome)):
            guard hasCompletedWelcome else {
                state.phase = .welcome
                return []
            }
            return enterMain(&state)

        case (.welcome, .didFinishWelcome):
            return [.willRememberWelcomeCompleted] + enterMain(&state)

        case (.main, .didOpenItemNotification(let id)):
            return [.willShowItem(id)]

        case (_, .didOpenItemNotification(let id)):
            state.pendingItemID = id
            return []

        case (_, .didBecomeActive):
            return [.willRefreshServices]

        default:
            return []
        }
    }

    private nonisolated static func enterMain(_ state: inout State) -> [Command] {
        state.phase = .main
        guard let id = state.pendingItemID else { return [.willStartMain] }
        state.pendingItemID = nil
        return [.willStartMain, .willShowItem(id)]
    }
}
