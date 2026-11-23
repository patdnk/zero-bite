//
//  SettingsCoordinator.swift
//  ZeroBite
//
//  The Settings tab: its navigation stack (food alerts), its own alerts (an info message
//  and two confirmations), and carrying out what a confirmation confirms.
//

import Foundation
import Observation

enum SettingsRoute: Hashable {
    case foodAlerts
    case foodAlert(FoodAlert)
}

@MainActor
@Observable
final class SettingsCoordinator: EventDrivenCoordinator {

    weak var parent: MainCoordinator?
    let loop = EventLoop<State, Event, Command>(State())

    @ObservationIgnored private let inventory: InventoryStore

    init(parent: MainCoordinator? = nil, inventory: InventoryStore) {
        self.parent = parent
        self.inventory = inventory
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willClearUsedHistory(let ids):
            .perform { [inventory] in
                for id in ids {
                    if let item = inventory.item(id: id) { inventory.delete(item) }
                }
            }
        case .willDeleteEverything:
            .perform { [inventory] in inventory.deleteAll() }
        case .willShowHowItWorks:
            .perform { [weak self] in self?.parent?.eventLoop(event: .didRequestHowItWorks) }
        }
    }
}

// MARK: - State

extension SettingsCoordinator {

    struct State: Equatable {
        var path: [SettingsRoute] = []
        var alert: Alert?
    }

    enum Alert: Identifiable, Equatable, Sendable {
        case notificationsOff
        /// The used-up items as they were when the user asked, so "Clear" removes exactly the
        /// ones the message counted.
        case confirmClearUsedHistory(itemIDs: [UUID])
        case confirmDeleteEverything

        var id: String {
            switch self {
            case .notificationsOff: "notificationsOff"
            case .confirmClearUsedHistory: "confirmClearUsedHistory"
            case .confirmDeleteEverything: "confirmDeleteEverything"
            }
        }

        var title: String {
            switch self {
            case .notificationsOff: String(localized: "Notifications are off")
            case .confirmClearUsedHistory: String(localized: "Clear used-up history?")
            case .confirmDeleteEverything: String(localized: "Delete everything?")
            }
        }

        var message: String {
            switch self {
            case .notificationsOff:
                String(localized: "Allow notifications for Zero Bite in the Settings app to get expiry reminders.")
            case .confirmClearUsedHistory(let itemIDs):
                String(localized: "Removes \(itemIDs.count) items you've already marked as used.")
            case .confirmDeleteEverything:
                String(localized: "This removes every item from My food. It can't be undone.")
            }
        }

        /// The destructive action a confirmation asks about; nil for an info message.
        var actionTitle: String? {
            switch self {
            case .notificationsOff: nil
            case .confirmClearUsedHistory: String(localized: "Clear")
            case .confirmDeleteEverything: String(localized: "Delete all")
            }
        }
    }
}

// MARK: - Events and commands

extension SettingsCoordinator {

    enum Event: Equatable, Sendable {
        /// The navigation stack changed its own path (a link, the back button, swipe back).
        case didChangePath([SettingsRoute])
        case didSelectFoodAlert(FoodAlert)
        /// The user turned notifications on but the system permission was refused.
        case didFindNotificationsDenied
        case didTapClearUsedHistory(itemIDs: [UUID])
        case didTapDeleteEverything
        /// Carries the alert itself: SwiftUI may clear the presentation (didDismissAlert)
        /// before it runs the button's action.
        case didConfirmAlert(Alert)
        case didDismissAlert
        case didTapHowItWorks
    }

    enum Command: Equatable {
        case willClearUsedHistory([UUID])
        case willDeleteEverything
        case willShowHowItWorks
    }
}

// MARK: - State machine

extension SettingsCoordinator {

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch event {
        case .didChangePath(let path):
            state.path = path
            return []

        case .didSelectFoodAlert(let alert):
            state.path.append(.foodAlert(alert))
            return []

        case .didFindNotificationsDenied:
            return show(.notificationsOff, in: &state)

        case .didTapClearUsedHistory(let itemIDs):
            guard !itemIDs.isEmpty else { return [] }
            return show(.confirmClearUsedHistory(itemIDs: itemIDs), in: &state)

        case .didTapDeleteEverything:
            return show(.confirmDeleteEverything, in: &state)

        case .didConfirmAlert(let alert):
            // Acts on the alert the button belonged to, whether or not the presentation has
            // already been cleared.
            state.alert = nil
            switch alert {
            case .notificationsOff: return []
            case .confirmClearUsedHistory(let itemIDs): return [.willClearUsedHistory(itemIDs)]
            case .confirmDeleteEverything: return [.willDeleteEverything]
            }

        case .didDismissAlert:
            state.alert = nil
            return []

        case .didTapHowItWorks:
            return [.willShowHowItWorks]
        }
    }

    private nonisolated static func show(_ alert: Alert, in state: inout State) -> [Command] {
        guard state.alert == nil else { return [] }
        state.alert = alert
        return []
    }
}
