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

    /// Everything ranking the catalogue reads. Ranking takes hundreds of milliseconds, far too long
    /// to repeat at every keystroke, so the result is kept until one of these changes.
    private struct MatchInputs: Equatable {
        struct Item: Equatable {
            let id: UUID
            let name: String
            let expiresAt: Date
        }
        let items: [Item]
        let soonThresholdDays: Int
        let day: Date
    }

    @ObservationIgnored private var ranked: (inputs: MatchInputs, matches: [RecipeMatch])?
    @ObservationIgnored private(set) var rankingCount = 0

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
        let active = store.activeItems
        let date = now()
        let inputs = MatchInputs(
            items: active.map { .init(id: $0.id, name: $0.name, expiresAt: $0.expiresAt) },
            soonThresholdDays: soonThresholdDays,
            day: Calendar.current.startOfDay(for: date)
        )
        if let ranked, ranked.inputs == inputs { return ranked.matches }
        let matches = RecipeMatcher(soonThresholdDays: soonThresholdDays, now: date)
            .match(recipes: recipes, inventory: active)
        ranked = (inputs, matches)
        rankingCount += 1
        return matches
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
            return RecipeMatcher.recipe(match.recipe, matchesQuery: query)
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
        case .ready: return String(localized: "Add a few more items, or check the “Nearly” tab for recipes that need one more ingredient.")
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
