//
//  LocalizationTests.swift
//  ZeroBiteTests
//
//  Polish plural forms from the app's compiled string catalog (pl.lproj), independent of the
//  simulator language, plus the English output they must not change.
//

import Foundation
import Testing
@testable import ZeroBite

struct LocalizationTests {

    private static func lproj(_ language: String) -> Bundle {
        let path = Bundle(for: InventoryStore.self).path(forResource: language, ofType: "lproj")!
        return Bundle(path: path)!
    }

    private let pl = Self.lproj("pl")
    private let en = Self.lproj("en")
    private let plLocale = Locale(identifier: "pl_PL")
    private let enLocale = Locale(identifier: "en_GB")

    private func polish(_ quantity: Double, _ unit: String) -> String {
        ItemDraft.quantityText(quantity, unit: unit, bundle: pl, locale: plLocale)
    }

    private func english(_ quantity: Double, _ unit: String) -> String {
        ItemDraft.quantityText(quantity, unit: unit, bundle: en, locale: enLocale)
    }

    @Test func polishSlicesFollowTheCount() {
        #expect(polish(1, "slices") == "1 plaster")
        #expect(polish(2, "slices") == "2 plastry")
        #expect(polish(5, "slices") == "5 plastrów")
        #expect(polish(22, "slices") == "22 plastry")
        #expect(polish(1.5, "slices") == "1,5 plastra")
    }

    @Test func polishLoavesFollowTheCount() {
        #expect(polish(1, "loaf") == "1 bochenek")
        #expect(polish(2, "loaf") == "2 bochenki")
        #expect(polish(5, "loaf") == "5 bochenków")
        #expect(polish(1.5, "loaf") == "1,5 bochenka")
    }

    @Test func polishAbbreviationsAndBareLabelsDoNotInflect() {
        #expect(polish(5, "pcs") == "5 szt.")
        #expect(polish(1, "bag") == "1 opak.")
        #expect(polish(0.5, "kg") == "0,5 kg")
        #expect(ItemDraft.unitLabel("slices", bundle: pl) == "plastry")
    }

    @Test func englishQuantitiesAreUnchanged() {
        #expect(english(1, "slices") == "1 slices")
        #expect(english(1.5, "slices") == "1.5 slices")
        #expect(english(2, "loaf") == "2 loaf")
        #expect(english(2, "pints") == "2 pints")
        #expect(english(3, "pcs") == "3 pcs")
        #expect(english(1.25, "kg") == "1.25 kg")
    }

    @Test func countUnitKeepsThePolishPluralForm() {
        func daysLeft(_ count: Int) -> String {
            CountUnit.unit(in: String(localized: "\(count) days left", bundle: pl, locale: plLocale), count: count)
        }
        func servings(_ count: Int) -> String {
            CountUnit.unit(in: String(localized: "\(count) servings", bundle: pl, locale: plLocale), count: count)
        }
        #expect([1, 2, 5, 12, 22, 25].map(daysLeft) == ["dzień", "dni", "dni", "dni", "dni", "dni"])
        #expect([1, 2, 5, 12, 22, 25].map(servings) == ["porcja", "porcje", "porcji", "porcji", "porcje", "porcji"])
    }

    @Test func countUnitInEnglish() {
        func daysLeft(_ count: Int) -> String {
            CountUnit.unit(in: String(localized: "\(count) days left", bundle: en, locale: enLocale), count: count)
        }
        #expect(daysLeft(1) == "day")
        #expect(daysLeft(2) == "days")
        #expect(CountUnit.unit(in: String(localized: "\(1) days over", bundle: en, locale: enLocale), count: 1) == "day over")
    }
}
