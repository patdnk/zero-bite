//
//  RecipeMatcherTests.swift
//  ZeroBiteTests
//

import Foundation
import Testing
@testable import ZeroBite

struct RecipeMatcherTests {

    private let now = TestSupport.date(2026, 9, 7)

    private let omelette = Recipe(
        id: "omelette", title: "Omelette", summary: "",
        ingredients: [
            .init(name: "eggs", amount: "3"),
            .init(name: "cheese", amount: "30g", optional: true),
            .init(name: "butter", amount: "1 tbsp"),
            .init(name: "salt", amount: "pinch"),
        ],
        steps: [], prepMinutes: 10, servings: 1, tags: []
    )

    private let stirFry = Recipe(
        id: "stirfry", title: "Stir-fry", summary: "",
        ingredients: [
            .init(name: "chicken", amount: "2"),
            .init(name: "bell pepper", amount: "1"),
            .init(name: "soy sauce", amount: "2 tbsp"),
        ],
        steps: [], prepMinutes: 20, servings: 2, tags: []
    )

    private func matcher() -> RecipeMatcher { RecipeMatcher(soonThresholdDays: 3, now: now) }

    @Test func nameMatchingHandlesPluralsPhrasesAndSynonyms() {
        #expect(RecipeMatcher.matches(ingredient: "eggs", itemName: "Egg"))
        #expect(RecipeMatcher.matches(ingredient: "tomatoes", itemName: "Cherry tomatoes"))
        #expect(RecipeMatcher.matches(ingredient: "cheese", itemName: "Cheddar cheese"))
        #expect(RecipeMatcher.matches(ingredient: "cheese", itemName: "Parmesan"))
        #expect(RecipeMatcher.matches(ingredient: "bell pepper", itemName: "Red pepper"))
        #expect(RecipeMatcher.matches(ingredient: "chicken", itemName: "Chicken breast"))
        #expect(!RecipeMatcher.matches(ingredient: "eggs", itemName: "Eggplant"))
        #expect(!RecipeMatcher.matches(ingredient: "milk", itemName: "Bread"))
    }

    @Test func staplesAreNeverMissing() {
        #expect(RecipeMatcher.isStaple("salt"))
        #expect(RecipeMatcher.isStaple("Olive oil"))
        #expect(RecipeMatcher.isStaple("soy sauce"))
        #expect(!RecipeMatcher.isStaple("chicken"))
    }

    @Test func canMakeWhenRequiredIngredientsArePresent() {
        let fridge = [
            TestSupport.item("Eggs", daysUntilExpiry: 10, from: now),
            TestSupport.item("Butter", daysUntilExpiry: 30, from: now),
        ]
        let match = matcher().match(recipe: omelette, inventory: fridge)
        #expect(match.canMake)
        #expect(match.missing.isEmpty)
        #expect(match.missingOptional.map(\.name) == ["cheese"])
        #expect(match.matched.contains { $0.ingredient.name == "salt" && $0.isStaple })
        #expect(match.usedItems.count == 2)
    }

    @Test func missingRequiredIngredientBlocksCanMake() {
        let fridge = [TestSupport.item("Chicken breast", daysUntilExpiry: 1, from: now)]
        let match = matcher().match(recipe: stirFry, inventory: fridge)
        #expect(!match.canMake)
        #expect(match.missing.map(\.name) == ["bell pepper"])
    }

    @Test func expiringItemsBoostRankingAndPreferSoonestItem() {
        let oldEggs = TestSupport.item("Eggs", daysUntilExpiry: 1, from: now)
        let freshEggs = TestSupport.item("Eggs", daysUntilExpiry: 20, from: now)
        let fridge = [
            freshEggs, oldEggs,
            TestSupport.item("Butter", daysUntilExpiry: 30, from: now),
            TestSupport.item("Chicken breast", daysUntilExpiry: 20, from: now),
            TestSupport.item("Red pepper", daysUntilExpiry: 20, from: now),
        ]
        let ranked = matcher().match(recipes: [stirFry, omelette], inventory: fridge)
        #expect(ranked.first?.recipe.id == "omelette")
        #expect(ranked.first?.expiringItems.map(\.id) == [oldEggs.id])
        #expect(ranked.first?.matched.first { $0.ingredient.name == "eggs" }?.item?.id == oldEggs.id)
    }

    @Test func consumedItemsAreIgnored() {
        let fridge = [
            TestSupport.item("Eggs", daysUntilExpiry: 10, from: now, consumed: true),
            TestSupport.item("Butter", daysUntilExpiry: 30, from: now),
        ]
        let match = matcher().match(recipe: omelette, inventory: fridge)
        #expect(!match.canMake)
        #expect(match.missing.map(\.name) == ["eggs"])
    }

    @Test func bundledCatalogueLoadsAndIsWellFormed() {
        let recipes = RecipeStore.loadBundled()
        #expect(recipes.count >= 30)
        #expect(Set(recipes.map(\.id)).count == recipes.count)
        for recipe in recipes {
            #expect(!recipe.requiredIngredients.isEmpty, "\(recipe.id) has no required ingredients")
            #expect(!recipe.steps.isEmpty, "\(recipe.id) has no steps")
        }
    }
}

/// Ranking the catalogue is far too expensive to repeat at every keystroke.
@MainActor
struct RecipesViewModelRankingTests {

    private final class Clock {
        var now = TestSupport.date(2026, 9, 7)
    }

    private let recipes = [
        Recipe(id: "omelette", title: "Omelette", summary: "",
               ingredients: [.init(name: "eggs", amount: "3"), .init(name: "butter", amount: "1 tbsp")],
               steps: [], prepMinutes: 10, servings: 1, tags: []),
        Recipe(id: "stirfry", title: "Stir-fry", summary: "",
               ingredients: [.init(name: "chicken", amount: "2"), .init(name: "bell pepper", amount: "1")],
               steps: [], prepMinutes: 20, servings: 2, tags: []),
    ]

    private func make() -> (RecipesViewModel, InventoryStore, AppSettings, Clock) {
        let clock = Clock()
        let store = InventoryStore.inMemory()
        store.loadSampleData(now: clock.now)
        let settings = AppSettings.ephemeral()
        let viewModel = RecipesViewModel(store: store, settings: settings, recipes: recipes, coordinator: RecipesCoordinator(), now: { clock.now })
        viewModel.scope = .all
        return (viewModel, store, settings, clock)
    }

    @Test func typingAndChangingScopeDoNotRankAgain() {
        let (viewModel, _, _, _) = make()
        #expect(viewModel.displayedMatches.count == 2)
        #expect(viewModel.rankingCount == 1)

        for text in ["st", "sti", "stir"] {
            viewModel.searchText = text
            #expect(viewModel.displayedMatches.map(\.id) == ["stirfry"])
        }
        viewModel.searchText = ""
        viewModel.scope = .ready
        _ = viewModel.displayedMatches

        #expect(viewModel.rankingCount == 1)
    }

    @Test func aChangeToWhatRankingReadsRanksAgain() throws {
        let (viewModel, store, settings, clock) = make()
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 1)

        let item = try #require(store.activeItems.first)
        store.markConsumed(item, at: clock.now)
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 2)

        let other = try #require(store.activeItems.first)
        other.name = "Renamed"
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 3)

        other.expiresAt = other.expiresAt.addingTimeInterval(86_400)
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 4)

        settings.soonThresholdDays += 1
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 5)

        clock.now = clock.now.addingTimeInterval(60)
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 5, "the same day")

        clock.now = clock.now.addingTimeInterval(86_400)
        _ = viewModel.displayedMatches
        #expect(viewModel.rankingCount == 6, "a new day")
    }

    @Test func keptMatchesEqualAFreshRanking() {
        let (viewModel, store, settings, clock) = make()
        _ = viewModel.displayedMatches
        viewModel.searchText = "o"
        _ = viewModel.displayedMatches
        let fresh = RecipeMatcher(soonThresholdDays: settings.soonThresholdDays, now: clock.now)
            .match(recipes: recipes, inventory: store.activeItems)
        #expect(viewModel.matches == fresh)
    }
}
