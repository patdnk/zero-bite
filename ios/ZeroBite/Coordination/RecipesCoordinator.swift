//
//  RecipesCoordinator.swift
//  ZeroBite
//
//  The Recipes tab's navigation stack. Sheets (edit, cooked) belong to MainCoordinator.
//

import Foundation
import Observation

enum RecipesRoute: Hashable {
    case recipe(String)
    case itemDetail(UUID)
}

@MainActor
@Observable
final class RecipesCoordinator: EventDrivenCoordinator {

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

extension RecipesCoordinator {

    struct State: Equatable {
        var path: [RecipesRoute] = []
    }

    enum Event: Equatable, Sendable {
        case didSelectRecipe(String)
        case didSelectItem(UUID)
        /// The navigation stack changed its own path (back button, swipe back).
        case didChangePath([RecipesRoute])
        /// The item on screen was deleted: leave its page.
        case didDeleteItem
        case didTapEditItem(UUID)
        case didTapCooked(recipeID: String)
    }

    enum Command: Equatable {
        case willPresent(MainCoordinator.Sheet)
    }

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch event {
        case .didSelectRecipe(let id):
            state.path.append(.recipe(id))
            return []
        case .didSelectItem(let id):
            state.path.append(.itemDetail(id))
            return []
        case .didChangePath(let path):
            state.path = path
            return []
        case .didDeleteItem:
            if !state.path.isEmpty { state.path.removeLast() }
            return []
        case .didTapEditItem(let id):
            return [.willPresent(.editItem(id))]
        case .didTapCooked(let recipeID):
            return [.willPresent(.cooked(recipeID: recipeID))]
        }
    }
}
