//
//  InventoryCoordinator.swift
//  ZeroBite
//
//  The Fridge tab's navigation stack. Sheets (scan, add, edit, cooked) belong to
//  MainCoordinator, so this asks for them with parent?.eventLoop(event: .didRequestSheet).
//

import Foundation
import Observation

enum InventoryRoute: Hashable {
    case itemDetail(UUID)
    case recipe(String)
}

@MainActor
@Observable
final class InventoryCoordinator: EventDrivenCoordinator {

    weak var parent: MainCoordinator?
    let loop = EventLoop<State, Event, Command>(State())

    init(parent: MainCoordinator? = nil) {
        self.parent = parent
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willPresent(let sheet):
            .perform { [weak self] in self?.parent?.eventLoop(event: .didRequestSheet(sheet)) }
        }
    }
}

extension InventoryCoordinator {

    struct State: Equatable {
        var path: [InventoryRoute] = []
    }

    enum Event: Equatable, Sendable {
        case didSelectItem(UUID)
        case didSelectRecipe(String)
        /// The navigation stack changed its own path (back button, swipe back).
        case didChangePath([InventoryRoute])
        /// The item on screen was deleted: leave its page.
        case didDeleteItem
        /// A notification was tapped: show just that item.
        case didRequestItem(UUID)
        case didTapScan
        case didTapAddItem(now: Date)
        case didTapEditItem(UUID)
        case didTapCooked(recipeID: String)
    }

    enum Command: Equatable {
        case willPresent(MainCoordinator.Sheet)
    }

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch event {
        case .didSelectItem(let id):
            state.path.append(.itemDetail(id))
            return []
        case .didSelectRecipe(let id):
            state.path.append(.recipe(id))
            return []
        case .didChangePath(let path):
            state.path = path
            return []
        case .didDeleteItem:
            if !state.path.isEmpty { state.path.removeLast() }
            return []
        case .didRequestItem(let id):
            state.path = [.itemDetail(id)]
            return []
        case .didTapScan:
            return [.willPresent(.scan)]
        case .didTapAddItem(let now):
            return [.willPresent(.addItem(ItemDraft(now: now)))]
        case .didTapEditItem(let id):
            return [.willPresent(.editItem(id))]
        case .didTapCooked(let recipeID):
            return [.willPresent(.cooked(recipeID: recipeID))]
        }
    }
}
