//
//  PolishRecipeTests.swift
//  ZeroBiteTests
//
//  Polish wiring: the recipe overlay and tag names, Polish ingredient aliases (golden fixture
//  testdata/recipe-matching-pl.json), Polish notification copy (testdata/notification-plans-pl.json)
//  and the Polish sample fridge. Everything reads the app bundle's files directly, so it holds
//  whatever language the simulator runs in.
//

import Foundation
import Testing
@testable import ZeroBite

private enum AppBundle {
    static let app = Bundle(for: InventoryStore.self)

    static func lproj(_ language: String) -> Bundle {
        Bundle(path: app.path(forResource: language, ofType: "lproj")!)!
    }
}

struct PolishIngredientAliasTests {
    struct Fixture: Decodable {
        struct NameMatch: Decodable { let ingredient: String; let item: String; let matches: Bool }
        let nameMatches: [NameMatch]
    }

    private let aliases = IngredientAliases.load(bundle: AppBundle.app)

    @Test func goldenPolishNameMatches() throws {
        let fixture = try Golden.load("recipe-matching-pl", as: Fixture.self)
        #expect(fixture.nameMatches.count >= 20)
        for m in fixture.nameMatches {
            #expect(RecipeMatcher.matches(ingredient: m.ingredient, itemName: m.item, aliases: aliases) == m.matches,
                    "\(m.ingredient) ~ \(m.item)")
            // The default (bundled) aliases are the same file.
            #expect(RecipeMatcher.matches(ingredient: m.ingredient, itemName: m.item) == m.matches, "default: \(m.ingredient) ~ \(m.item)")
        }
    }

    @Test func aliasesAreInjectable() {
        #expect(RecipeMatcher.matches(ingredient: "eggs", itemName: "Jajka", aliases: aliases))
        #expect(!RecipeMatcher.matches(ingredient: "eggs", itemName: "Jajka", aliases: .none))
        #expect(!RecipeMatcher.matches(ingredient: "eggs", itemName: "Bakłażan", aliases: aliases))

        let custom = IngredientAliases(["egg": ["huevos"]])
        #expect(RecipeMatcher.matches(ingredient: "eggs", itemName: "Huevos camperos", aliases: custom))
        #expect(!RecipeMatcher.matches(ingredient: "eggs", itemName: "Jajka", aliases: custom))
    }

    @Test func synonymGroupMembersShareAliases() {
        // "makaron" is listed under "pasta"; "spaghetti" is in pasta's synonym group.
        #expect(RecipeMatcher.matches(ingredient: "spaghetti", itemName: "Makaron", aliases: aliases))
        // "Pierś z kurczaka" reaches "chicken breast" through the chicken group.
        #expect(RecipeMatcher.matches(ingredient: "chicken breast", itemName: "Pierś z kurczaka", aliases: aliases))
    }

    @Test func matcherUsesItsInjectedAliases() {
        let now = TestSupport.date(2026, 9, 7)
        let eggs = TestSupport.item("Jajka", daysUntilExpiry: 3, from: now)
        let omelette = Recipe(id: "o", title: "Omelette", summary: "", ingredients: [.init(name: "eggs")],
                              steps: [], prepMinutes: 5, servings: 1, tags: [])
        #expect(RecipeMatcher(now: now, aliases: aliases).match(recipe: omelette, inventory: [eggs]).canMake)
        #expect(!RecipeMatcher(now: now, aliases: .none).match(recipe: omelette, inventory: [eggs]).canMake)
    }
}

struct PolishRecipeCatalogueTests {

    private let english = RecipeStore.loadBundled(bundle: AppBundle.app, language: "en")
    private let polish = RecipeStore.loadBundled(bundle: AppBundle.app, language: "pl")

    @Test func englishCatalogueIsTheSourceFile() throws {
        let url = try #require(AppBundle.app.url(forResource: "recipes", withExtension: "json"))
        let source = try JSONDecoder().decode([Recipe].self, from: Data(contentsOf: url))
        #expect(english.count == 35)
        for (recipe, original) in zip(english, source) {
            #expect(recipe.title == original.title)
            #expect(recipe.summary == original.summary)
            #expect(recipe.steps == original.steps)
            #expect(recipe.displayTags == original.tags)
            #expect(recipe.ingredients.map(\.name) == original.ingredients.map(\.name))
            #expect(recipe.ingredients.map(\.amount) == original.ingredients.map(\.amount))
            #expect(recipe.ingredients.allSatisfy { $0.label == $0.name && $0.missingLabel == $0.name })
        }
    }

    @Test func polishOverlayTranslatesTextButKeepsMatchingKeys() throws {
        #expect(polish.map(\.id) == english.map(\.id))
        let omelette = try #require(polish.first { $0.id == "classic-omelette" })
        #expect(omelette.title == "Klasyczny omlet z serem")
        #expect(omelette.canonicalTitle == english.first { $0.id == "classic-omelette" }?.title)
        let eggs = try #require(omelette.ingredients.first { $0.name == "eggs" })
        #expect(eggs.amount == "3")
        #expect(eggs.label == "jajka")
        #expect(eggs.missingLabel == "jajek")

        for (pl, en) in zip(polish, english) {
            #expect(!pl.title.isEmpty && !pl.summary.isEmpty, "\(pl.id)")
            #expect(pl.steps.count == en.steps.count, "\(pl.id)")
            #expect(pl.ingredients.map(\.name) == en.ingredients.map(\.name), "\(pl.id)")
            #expect(pl.ingredients.map(\.optional) == en.ingredients.map(\.optional), "\(pl.id)")
            #expect(pl.tags == en.tags, "\(pl.id)")
            #expect(pl.symbol == en.symbol, "\(pl.id): artwork follows the English title")
        }
    }

    @Test func polishTagNames() throws {
        let omelette = try #require(polish.first { $0.id == "classic-omelette" })
        let english = try #require(self.english.first { $0.id == "classic-omelette" })
        #expect(omelette.tags == english.tags)
        #expect(omelette.displayTags.count == omelette.tags.count)
        #expect(omelette.displayTags != omelette.tags)
        let tagged = try #require(polish.first { $0.tags.contains("no-cook") })
        #expect(tagged.displayTags[tagged.tags.firstIndex(of: "no-cook")!] == "bez gotowania")
    }

    @Test func searchFindsPolishIngredientsInTheirDictionaryForm() throws {
        let omelette = try #require(polish.first { $0.id == "classic-omelette" })
        let aliases = IngredientAliases.load(bundle: AppBundle.app)
        // The ingredient list says "mleka" and "masła"; people search "mleko" and "maslo".
        for query in ["omlet", "mleka", "mleko", "masło", "maslo", "jajko", "milk"] {
            #expect(RecipeMatcher.recipe(omelette, matchesQuery: query, aliases: aliases), "\(query)")
        }
        #expect(!RecipeMatcher.recipe(omelette, matchesQuery: "makaron", aliases: aliases))
        #expect(!RecipeMatcher.recipe(omelette, matchesQuery: "mleko", aliases: .none))
    }

    @Test func missingListsUseTheGenitiveInPolishAndTheNameInEnglish() throws {
        let now = TestSupport.date(2026, 9, 7)
        let fridge = [TestSupport.item("Masło", daysUntilExpiry: 10, from: now)]
        let matcher = RecipeMatcher(now: now)
        let pl = matcher.match(recipe: try #require(polish.first { $0.id == "classic-omelette" }), inventory: fridge)
        let en = matcher.match(recipe: try #require(english.first { $0.id == "classic-omelette" }), inventory: fridge)
        #expect(pl.missing.map(\.missingLabel) == ["jajek"])
        #expect(en.missing.map(\.missingLabel) == ["eggs"])
        #expect(pl.missing.map(\.name) == en.missing.map(\.name))
    }
}

@MainActor
struct PolishSampleFridgeTests {

    /// For every sample item, the recipe ingredients its English name matches must equal the ones
    /// its Polish name matches, so the Polish sample fridge finds the same recipes.
    @Test func polishSampleNamesMatchTheSameIngredientsAsEnglish() {
        let now = TestSupport.date(2026, 9, 7)
        let english = SampleData.items(now: now, bundle: AppBundle.lproj("en"))
        let polish = SampleData.items(now: now, bundle: AppBundle.lproj("pl"))
        #expect(english.count == polish.count)
        #expect(polish.first { $0.name == "Jajka" } != nil)

        let recipes = RecipeStore.loadBundled(bundle: AppBundle.app, language: "en")
        let ingredients = Set(recipes.flatMap { $0.ingredients.map(\.name) }).filter { !RecipeMatcher.isStaple($0) }.sorted()
        // English-only matches where the item name sits inside a longer ingredient name ("Onion" in
        // "spring onion"). Aliases only match one way, so "Cebula" rightly does not count as dymka.
        let englishOnly: [String: Set<String>] = ["Onion": ["spring onion"]]
        for (en, pl) in zip(english, polish) {
            let enMatches = ingredients.filter {
                RecipeMatcher.matches(ingredient: $0, itemName: en.name) && !(englishOnly[en.name]?.contains($0) ?? false)
            }
            let plMatches = ingredients.filter { RecipeMatcher.matches(ingredient: $0, itemName: pl.name) }
            #expect(enMatches == plMatches, "\(en.name) → \(enMatches) vs \(pl.name) → \(plMatches)")
        }
    }

    @Test func polishSampleFridgeRanksRecipesLikeTheEnglishOne() {
        let now = TestSupport.date(2026, 9, 7)
        let matcher = RecipeMatcher(now: now)
        let english = matcher.match(recipes: RecipeStore.loadBundled(bundle: AppBundle.app, language: "en"),
                                    inventory: SampleData.items(now: now, bundle: AppBundle.lproj("en")))
        let polish = matcher.match(recipes: RecipeStore.loadBundled(bundle: AppBundle.app, language: "pl"),
                                   inventory: SampleData.items(now: now, bundle: AppBundle.lproj("pl")))
        let summary = { (matches: [RecipeMatch]) in
            Dictionary(uniqueKeysWithValues: matches.map { ($0.recipe.id, "\($0.score)|\($0.missing.map(\.name))") })
        }
        #expect(summary(polish) == summary(english))
    }
}

struct PolishNotificationTests {

    @Test func polishNotificationPlans() throws {
        let fixture = try Golden.load("notification-plans-pl", as: GoldenNotificationTests.Fixture.self)
        #expect(fixture.timeZone == TestSupport.calendar.timeZone.identifier)
        let copy = NotificationCopy(bundle: AppBundle.lproj("pl"), locale: Locale(identifier: "pl_PL"))
        let planner = ExpiryNotificationPlanner(calendar: TestSupport.calendar, copy: copy)
        let now = Golden.localDateTime(fixture.now)
        for c in fixture.cases {
            let uuids = Dictionary(uniqueKeysWithValues: c.items.map { ($0.id, UUID()) })
            let items = c.items.map { ExpiringItemSummary(id: uuids[$0.id]!, name: $0.name, expiresAt: Golden.day($0.expiresOn)) }
            let p = c.preferences
            let planned = planner.plan(
                items: items,
                preferences: NotificationPreferences(enabled: p.enabled, leadDays: p.leadDays, hour: p.hour, minute: p.minute),
                now: now
            )
            #expect(planned.count == c.expected.count, "\(c.name)")
            for (actual, expected) in zip(planned, c.expected) {
                let uuid = uuids[expected.itemId]!
                #expect(actual.id == "\(ExpiryNotificationPlanner.identifierPrefix)\(uuid.uuidString).\(expected.suffix)", "\(c.name)")
                #expect(actual.title == expected.title, "\(c.name)")
                #expect(actual.body == expected.body, "\(c.name)")
                #expect(actual.fireDate == Golden.localDateTime(expected.fireAt), "\(c.name)")
            }
        }
    }

    @Test func polishSoonTitleCoversEveryLeadLength() {
        let copy = NotificationCopy(bundle: AppBundle.lproj("pl"), locale: Locale(identifier: "pl_PL"))
        #expect(copy.soonTitle(name: "Mleko", leadDays: 1) == "Mleko: termin mija jutro")
        #expect([2, 5, 7, 12, 22].map { copy.soonTitle(name: "Mleko", leadDays: $0) }
                == [2, 5, 7, 12, 22].map { "Mleko: termin mija za \($0) dni" })
    }

    @Test func englishCopyFromTheEnglishTableIsUnchanged() {
        let copy = NotificationCopy(bundle: AppBundle.lproj("en"), locale: Locale(identifier: "en_GB"))
        #expect(copy.soonTitle(name: "Milk", leadDays: 1) == "Milk expires tomorrow")
        #expect(copy.soonTitle(name: "Milk", leadDays: 3) == "Milk expires in 3 days")
        #expect(copy.soonBody == "Plan a meal around it — Zero Bite has recipe ideas.")
        #expect(copy.todayTitle(name: "Milk") == "Milk expires today")
        #expect(copy.todayBody == "Use it today or freeze it.")
        #expect(copy.expiredTitle(name: "Milk") == "Milk has expired")
        #expect(copy.expiredBody == "Check it and update your fridge.")
    }

    @Test func polishRecallAndTestNotificationCopy() {
        let copy = NotificationCopy(bundle: AppBundle.lproj("pl"), locale: Locale(identifier: "pl_PL"))
        #expect(copy.recallTitle(itemName: "Mleko", isLikely: true) == "Wycofanie – sprawdź: Mleko")
        #expect(copy.recallTitle(itemName: "Mleko", isLikely: false) == "Możliwe wycofanie: Mleko")
        #expect(copy.recallBody(alertTitle: "Dairy Co recalls Milk") == "Dairy Co recalls Milk. Sprawdź opakowanie, zanim to zjesz.")
        #expect(copy.testTitle == "Przypomnienia Zero Bite są włączone")
        #expect(copy.testBody == "Tak damy ci znać o jedzeniu, któremu kończy się termin.")
    }

    @Test func englishRecallAndTestNotificationCopyIsUnchanged() {
        let copy = NotificationCopy(bundle: AppBundle.lproj("en"), locale: Locale(identifier: "en_GB"))
        #expect(copy.recallTitle(itemName: "Milk", isLikely: true) == "Recall: check your Milk")
        #expect(copy.recallTitle(itemName: "Milk", isLikely: false) == "Possible recall: Milk")
        #expect(copy.recallBody(alertTitle: "Dairy Co recalls Milk") == "Dairy Co recalls Milk. Check the pack before eating it.")
        #expect(copy.testTitle == "Zero Bite reminders are on")
        #expect(copy.testBody == "This is how you'll hear about food that's about to expire.")
    }
}
