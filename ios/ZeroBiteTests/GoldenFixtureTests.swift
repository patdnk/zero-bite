//
//  GoldenFixtureTests.swift
//  ZeroBiteTests
//
//  Runs the golden fixtures in /testdata — the same files the Android `:core` tests read —
//  against the Swift services, so both platforms are held to one set of expectations.
//

import Foundation
import Testing
@testable import ZeroBite

private final class GoldenBundleToken {}

enum Golden {
    static func load<T: Decodable>(_ name: String, as type: T.Type) throws -> T {
        let url = try #require(Bundle(for: GoldenBundleToken.self).url(forResource: name, withExtension: "json"))
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }

    /// "2026-09-07" → 09:00 London on that day (the TestSupport convention).
    static func day(_ iso: String) -> Date {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        return TestSupport.date(parts[0], parts[1], parts[2])
    }

    static func iso(_ date: Date) -> String {
        let c = TestSupport.calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    /// "2026-09-07T08:00" in London.
    static func localDateTime(_ text: String) -> Date {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_GB_POSIX")
        formatter.timeZone = TestSupport.calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm"
        return formatter.date(from: text)!
    }
}

struct GoldenExpiryDateTests {
    struct Fixture: Decodable {
        struct Case: Decodable {
            let name: String
            let lines: [String]
            let best: String?
            let count: Int?
            let bestSourceLine: String?
        }
        let today: String
        let cases: [Case]
    }

    @Test func expiryDates() throws {
        let fixture = try Golden.load("expiry-dates", as: Fixture.self)
        let parser = ExpiryDateParser(calendar: TestSupport.calendar, referenceDate: Golden.day(fixture.today))
        for c in fixture.cases {
            let candidates = parser.parse(lines: c.lines)
            #expect(candidates.first.map { Golden.iso($0.date) } == c.best, "\(c.name)")
            if let count = c.count { #expect(candidates.count == count, "\(c.name)") }
            if let line = c.bestSourceLine { #expect(candidates.first?.sourceLine == line, "\(c.name)") }
        }
    }

    struct NameFixture: Decodable {
        struct Case: Decodable { let name: String; let lines: [String]; let expected: String? }
        let cases: [Case]
    }

    @Test func productNames() throws {
        for c in try Golden.load("product-names", as: NameFixture.self).cases {
            #expect(ProductNameGuesser.guess(from: c.lines) == c.expected, "\(c.name)")
        }
    }
}

@MainActor
struct GoldenRecipeTests {
    struct Fixture: Decodable {
        struct NameMatch: Decodable { let ingredient: String; let item: String; let matches: Bool }
        struct Staple: Decodable { let name: String; let isStaple: Bool }
        struct Item: Decodable { let id: String; let name: String; let expiresInDays: Int; let consumed: Bool? }
        struct Expect: Decodable {
            let canMake: Bool?
            let missing: [String]?
            let missingOptional: [String]?
            let staples: [String]?
            let usedItemIds: [String]?
            let expiringItemIds: [String]?
            let ingredientItems: [String: String]?
        }
        struct Case: Decodable {
            let name: String
            let inventory: [Item]
            let recipes: [String]
            let ranking: [String]?
            let matches: [String: Expect]?
        }
        let today: String
        let soonThresholdDays: Int
        let nameMatches: [NameMatch]
        let staples: [Staple]
        let recipes: [Recipe]
        let cases: [Case]
    }

    @Test func recipeMatching() throws {
        let fixture = try Golden.load("recipe-matching", as: Fixture.self)
        for m in fixture.nameMatches {
            #expect(RecipeMatcher.matches(ingredient: m.ingredient, itemName: m.item) == m.matches, "\(m.ingredient) ~ \(m.item)")
        }
        for s in fixture.staples {
            #expect(RecipeMatcher.isStaple(s.name) == s.isStaple, "staple \(s.name)")
        }

        let now = Golden.day(fixture.today)
        let matcher = RecipeMatcher(soonThresholdDays: fixture.soonThresholdDays, now: now)
        for c in fixture.cases {
            var fixtureID: [UUID: String] = [:]
            let inventory = c.inventory.map { spec -> FoodItem in
                let item = FoodItem(
                    name: spec.name,
                    expiresAt: TestSupport.calendar.date(byAdding: .day, value: spec.expiresInDays, to: now)!,
                    consumedAt: spec.consumed == true ? now : nil
                )
                fixtureID[item.id] = spec.id
                return item
            }
            let recipes = c.recipes.compactMap { id in fixture.recipes.first { $0.id == id } }
            let ranked = matcher.match(recipes: recipes, inventory: inventory)
            if let ranking = c.ranking { #expect(ranked.map(\.recipe.id) == ranking, "\(c.name)") }

            for (recipeID, expect) in c.matches ?? [:] {
                let match = try #require(ranked.first { $0.recipe.id == recipeID }, "\(c.name)")
                let ids = { (items: [FoodItem]) in items.map { fixtureID[$0.id] ?? "?" } }
                if let v = expect.canMake { #expect(match.canMake == v, "\(c.name)") }
                if let v = expect.missing { #expect(match.missing.map(\.name) == v, "\(c.name)") }
                if let v = expect.missingOptional { #expect(match.missingOptional.map(\.name) == v, "\(c.name)") }
                if let v = expect.staples { #expect(match.matched.filter(\.isStaple).map(\.ingredient.name) == v, "\(c.name)") }
                if let v = expect.usedItemIds { #expect(ids(match.usedItems) == v, "\(c.name)") }
                if let v = expect.expiringItemIds { #expect(ids(match.expiringItems) == v, "\(c.name)") }
                for (ingredient, itemID) in expect.ingredientItems ?? [:] {
                    let item = match.matched.first { $0.ingredient.name == ingredient }?.item
                    #expect(item.flatMap { fixtureID[$0.id] } == itemID, "\(c.name): \(ingredient)")
                }
            }
        }
    }
}

struct GoldenNotificationTests {
    struct Fixture: Decodable {
        struct Prefs: Decodable { let enabled: Bool; let leadDays: Int; let hour: Int; let minute: Int }
        struct Item: Decodable { let id: String; let name: String; let expiresOn: String }
        struct Expected: Decodable { let itemId: String; let suffix: String; let title: String; let body: String; let fireAt: String }
        struct Case: Decodable { let name: String; let preferences: Prefs; let items: [Item]; let expected: [Expected] }
        let timeZone: String
        let now: String
        let cases: [Case]
    }

    @Test func notificationPlans() throws {
        let fixture = try Golden.load("notification-plans", as: Fixture.self)
        #expect(fixture.timeZone == TestSupport.calendar.timeZone.identifier)
        let planner = ExpiryNotificationPlanner(calendar: TestSupport.calendar)
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
                #expect(actual.itemID == uuid, "\(c.name)")
                #expect(actual.title == expected.title, "\(c.name)")
                #expect(actual.body == expected.body, "\(c.name)")
                #expect(actual.fireDate == Golden.localDateTime(expected.fireAt), "\(c.name)")
            }
        }
    }
}

@MainActor
struct GoldenRecallTests {
    struct Fixture: Decodable {
        struct Item: Decodable {
            let id: String?
            let name: String
            let expiresOn: String
            let brand: String?
            let barcode: String?
            let consumed: Bool?
        }
        struct Expect: Decodable { let confidence: String; let reasons: [String]?; let reasonsInclude: [String]? }
        struct MatchCase: Decodable {
            let name: String
            let today: String
            let alertId: String
            let productIndex: Int?
            let item: Item
            let expect: Expect?
        }
        struct Dismissed: Decodable { let alertId: String; let itemId: String }
        struct ListCase: Decodable {
            let name: String
            let today: String
            let items: [Item]
            let dismissed: [Dismissed]?
            let firstAlertId: String?
        }
        struct Spec: Decodable { let year: Int; let month: Int; let day: Int? }
        struct AlertDateCase: Decodable { let text: String; let expected: [Spec] }
        struct NameMatch: Decodable { let item: String; let product: String; let matches: Bool }
        struct AllergenCase: Decodable { let allergen: String; let label: String; let matches: Bool }
        struct Concern: Decodable { let alertId: String; let allergens: [String]; let expected: [String] }

        let matchCases: [MatchCase]
        let listCases: [ListCase]
        let alertDates: [AlertDateCase]
        let nameMatches: [NameMatch]
        let allergens: [AllergenCase]
        let concerns: [Concern]
    }

    private func makeItem(_ spec: Fixture.Item, id: UUID = UUID(), now: Date) -> FoodItem {
        FoodItem(
            id: id, name: spec.name, category: .dairy, addedAt: now,
            expiresAt: Golden.day(spec.expiresOn), barcode: spec.barcode, brand: spec.brand,
            consumedAt: spec.consumed == true ? now : nil
        )
    }

    private func confidenceName(_ confidence: RecallMatch.Confidence) -> String {
        confidence == .likely ? "likely" : "possible"
    }

    @Test func recallMatching() throws {
        let fixture = try Golden.load("recall-matching", as: Fixture.self)
        let alerts = try AlertFixtures.load()

        for c in fixture.matchCases {
            let now = Golden.day(c.today)
            let matcher = RecallMatcher(now: now, calendar: TestSupport.calendar)
            let alert = try #require(alerts.first { $0.id == c.alertId }, "\(c.name)")
            let products = c.productIndex.map { [alert.products[$0]] } ?? alert.products
            let results = products.compactMap { matcher.match(alert: alert, product: $0, item: makeItem(c.item, now: now)) }
            guard let expect = c.expect else {
                #expect(results.isEmpty, "\(c.name)")
                continue
            }
            let match = try #require(results.first, "\(c.name)")
            #expect(confidenceName(match.confidence) == expect.confidence, "\(c.name)")
            let reasons = Set(match.reasons.map(\.rawValue))
            if let exact = expect.reasons { #expect(reasons == Set(exact), "\(c.name)") }
            if let subset = expect.reasonsInclude { #expect(reasons.isSuperset(of: subset), "\(c.name)") }
        }

        for c in fixture.listCases {
            let now = Golden.day(c.today)
            let uuids = Dictionary(uniqueKeysWithValues: c.items.map { ($0.id ?? "item", UUID()) })
            let items = c.items.map { makeItem($0, id: uuids[$0.id ?? "item"]!, now: now) }
            let dismissed = Set((c.dismissed ?? []).map { RecallMatch.id(alertID: $0.alertId, itemID: uuids[$0.itemId]!) })
            let found = RecallMatcher(now: now, calendar: TestSupport.calendar).matches(alerts: alerts, items: items, dismissed: dismissed)
            #expect(found.first?.alert.id == c.firstAlertId, "\(c.name)")
            if c.firstAlertId == nil { #expect(found.isEmpty, "\(c.name)") }
        }

        for c in fixture.alertDates {
            let expected = Set(c.expected.map { AlertDateSpec(year: $0.year, month: $0.month, day: $0.day) })
            #expect(Set(AlertDateParser.dates(in: c.text)) == expected, "\(c.text)")
        }

        for c in fixture.nameMatches {
            let result = RecallMatcher.nameMatches(itemWords: RecallMatcher.words(c.item), productWords: RecallMatcher.words(c.product))
            #expect(result == c.matches, "\(c.item) ~ \(c.product)")
        }

        for c in fixture.allergens {
            let allergen = try #require(Allergen(rawValue: c.allergen))
            #expect(allergen.matches(alertLabel: c.label) == c.matches, "\(c.allergen) ~ \(c.label)")
        }

        for c in fixture.concerns {
            let alert = try #require(alerts.first { $0.id == c.alertId })
            let profile = c.allergens.compactMap(Allergen.init(rawValue:))
            #expect(alert.concerns(profile).map(\.rawValue) == c.expected, "\(c.alertId)")
        }
    }
}
