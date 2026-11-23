//
//  MainCoordinator.swift
//  ZeroBite
//
//  The tabs: which one is selected, the sheet on top of them, and the "how it works" replay.
//  Owns the three tab coordinators and starts a ScanCoordinator whenever the scan sheet
//  opens. Started by AppCoordinator once the app reaches the main phase.
//

import Foundation
import Observation

@MainActor
@Observable
final class MainCoordinator: EventDrivenCoordinator {

    weak var parent: AppCoordinator?
    let dependencies: AppDependencies
    let loop = EventLoop<State, Event, Command>(State())

    // MARK: - Children

    let inventory: InventoryCoordinator
    let recipes: RecipesCoordinator
    let settings: SettingsCoordinator
    /// Started by willStartScan. A stopped scan stays here, inert, until the next one starts,
    /// so the sheet can finish animating out.
    private(set) var scan: ScanCoordinator?

    init(parent: AppCoordinator? = nil, dependencies: AppDependencies) {
        self.parent = parent
        self.dependencies = dependencies
        inventory = InventoryCoordinator()
        recipes = RecipesCoordinator()
        settings = SettingsCoordinator(inventory: dependencies.inventory)
        inventory.parent = self
        recipes.parent = self
        settings.parent = self
    }

    func execute(_ command: Command) -> CommandTask<Event> {
        switch command {
        case .willStartScan:
            return .perform { [weak self] in
                guard let self else { return }
                scan = ScanFactory.makeCoordinator(parent: self, dependencies: dependencies)
            }

        case .willStopScan:
            // Stops any OCR or lookup still running. Ignored if the scan finished itself.
            return .perform { [weak self] in self?.scan?.eventLoop(event: .didDismiss) }

        case .willShowItem(let id):
            return .perform { [weak self] in self?.inventory.eventLoop(event: .didRequestItem(id)) }
        }
    }
}

// MARK: - State

extension MainCoordinator {

    struct State: Equatable {
        var selectedTab: Tab = .fridge
        var sheet: Sheet?
        /// Onboarding replayed from Settings, shown full screen over the tabs.
        var isShowingHowItWorks = false
    }

    enum Tab: Hashable, CaseIterable {
        case fridge, recipes, settings

        var title: String {
            switch self {
            case .fridge: String(localized: "My food")
            case .recipes: String(localized: "Recipes")
            case .settings: String(localized: "Settings")
            }
        }

        /// Language-independent name for accessibility identifiers.
        var identifier: String {
            switch self {
            case .fridge: "fridge"
            case .recipes: "recipes"
            case .settings: "settings"
            }
        }

        var symbol: String {
            switch self {
            case .fridge: "refrigerator"
            case .recipes: "frying.pan"
            case .settings: "gearshape"
            }
        }

        var selectedSymbol: String {
            switch self {
            case .fridge: "refrigerator.fill"
            case .recipes: "frying.pan.fill"
            case .settings: "gearshape.fill"
            }
        }
    }

    enum Sheet: Identifiable, Hashable, Sendable {
        case scan
        case addItem(ItemDraft)
        case editItem(UUID)
        case cooked(recipeID: String)

        var id: String {
            switch self {
            case .scan: "scan"
            case .addItem: "addItem"
            case .editItem(let id): "editItem-\(id.uuidString)"
            case .cooked(let recipeID): "cooked-\(recipeID)"
            }
        }
    }
}

// MARK: - Events and commands

extension MainCoordinator {

    enum Event: Equatable, Sendable {
        case didSelectTab(Tab)
        /// A tab asked for a sheet.
        case didRequestSheet(Sheet)
        /// The sheet was swiped away, cancelled, saved or done.
        case didDismissSheet
        case didFinishScan
        case didRequestHowItWorks
        case didFinishHowItWorks
        /// A notification was tapped: show that item on the Fridge tab.
        case didRequestItem(UUID)
    }

    enum Command: Equatable {
        case willStartScan
        case willStopScan
        case willShowItem(UUID)
    }
}

// MARK: - State machine

extension MainCoordinator {

    nonisolated static func stateMachine(_ state: inout State, _ event: Event) -> [Command] {
        switch event {
        case .didSelectTab(let tab):
            state.selectedTab = tab
            return []

        case .didRequestSheet(let sheet):
            // One sheet at a time.
            guard state.sheet == nil, !state.isShowingHowItWorks else { return [] }
            state.sheet = sheet
            return sheet == .scan ? [.willStartScan] : []

        case .didDismissSheet:
            return closeSheet(&state)

        case .didFinishScan:
            guard state.sheet == .scan else { return [] }
            return closeSheet(&state)

        case .didRequestHowItWorks:
            guard state.sheet == nil else { return [] }
            state.isShowingHowItWorks = true
            return []

        case .didFinishHowItWorks:
            state.isShowingHowItWorks = false
            return []

        case .didRequestItem(let id):
            state.selectedTab = .fridge
            state.isShowingHowItWorks = false
            return closeSheet(&state) + [.willShowItem(id)]
        }
    }

    private nonisolated static func closeSheet(_ state: inout State) -> [Command] {
        let wasScanning = state.sheet == .scan
        state.sheet = nil
        return wasScanning ? [.willStopScan] : []
    }
}
