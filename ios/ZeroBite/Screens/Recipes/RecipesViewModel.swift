//
//  RecipesViewModel.swift
//  ZeroBite
//
//  Recipes ranked against the fridge, with a scope filter and search.
//

import Foundation
import Observation

@MainActor
@Observable
final class RecipesViewModel {

    enum Scope: Hashable, CaseIterable {
        case ready, nearly, all

        var title: String {
            switch self {
            case .ready: String(localized: "Ready")
            case .nearly: String(localized: "Nearly")
            case .all: String(localized: "All")
            }
        }

        var sectionTitle: String {
            switch self {
            case .ready: String(localized: "You can make these now")
            case .nearly: String(localized: "Missing one or two things")
            case .all: String(localized: "All recipes")
            }
        }
    }

    var scope: Scope = .ready
    var searchText = ""

    private let store: InventoryStore
    private let settings: AppSettings
    private let recipes: [Recipe]
    private let coordinator: RecipesCoordinator
    private let now: () -> Date

    init(
        store: InventoryStore,
        settings: AppSettings,
        recipes: [Recipe],
        coordinator: RecipesCoordinator,
        now: @escaping () -> Date = { .now }
    ) {
        self.store = store
        self.settings = settings
        self.recipes = recipes
        self.coordinator = coordinator
        self.now = now
    }

    var soonThresholdDays: Int { settings.soonThresholdDays }

    var matches: [RecipeMatch] {
        RecipeMatcher(soonThresholdDays: soonThresholdDays, now: now())
            .match(recipes: recipes, inventory: store.activeItems)
    }

    var expiringItems: [FoodItem] {
        store.activeItems.filter { $0.status(soonThresholdDays: soonThresholdDays, now: now()).needsAttention }
    }

    var showsExpiringStrip: Bool {
        !expiringItems.isEmpty && searchText.isEmpty
    }

    var displayedMatches: [RecipeMatch] {
        let query = searchText.trimmingCharacters(in: .whitespaces).lowercased()
        return matches.filter { match in
            switch scope {
            case .ready: guard match.canMake else { return false }
            case .nearly: guard (1...2).contains(match.missing.count) else { return false }
            case .all: break
            }
            guard !query.isEmpty else { return true }
            return match.recipe.title.lowercased().contains(query)
                || match.recipe.ingredients.contains { $0.label.lowercased().contains(query) || $0.name.lowercased().contains(query) }
        }
    }

    var emptyTitle: String {
        switch scope {
        case .ready: String(localized: "Nothing to cook yet")
        case .nearly: String(localized: "No near-misses")
        case .all: String(localized: "No recipes match")
        }
    }

    var emptyMessage: String {
        if !searchText.isEmpty { return String(localized: "Try a different search.") }
        switch scope {
        case .ready: return String(localized: "Add a few more things to your fridge, or check the “Nearly” tab for recipes that need one more ingredient.")
        case .nearly: return String(localized: "Everything is either ready to cook or needs more than a couple of ingredients.")
        case .all: return String(localized: "The recipe catalogue is empty.")
        }
    }

    // MARK: - Actions

    func showRecipe(_ match: RecipeMatch) {
        coordinator.showRecipe(id: match.recipe.id)
    }

    func showItem(_ item: FoodItem) {
        coordinator.showItem(id: item.id)
    }
}
