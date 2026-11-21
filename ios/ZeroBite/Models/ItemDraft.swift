//
//  ItemDraft.swift
//  ZeroBite
//
//  Value-type editing buffer for the add/edit item form. Keeps SwiftData objects out of
//  the form until the user commits.
//

import Foundation

struct ItemDraft: Hashable, Sendable {
    var name = ""
    var category: FoodCategory = .other
    var storage: StorageLocation = .fridge
    var quantity: Double = 1
    var unit = "pcs"
    var expiresAt: Date
    var notes = ""
    var barcode: String?
    var brand: String?

    static let unitSuggestions = ["pcs", "g", "kg", "ml", "l", "pack", "slices"]

    /// Display name of a unit in the current language. Known units are stored as their English
    /// code (so data survives a language change); anything the user typed is shown as is.
    static func unitLabel(_ unit: String, bundle: Bundle = .main) -> String {
        switch unit {
        case "pcs": String(localized: "pcs", bundle: bundle, comment: "Unit: pieces")
        case "pack": String(localized: "pack", bundle: bundle, comment: "Unit: pack")
        case "slices": String(localized: "slices", bundle: bundle, comment: "Unit: slices")
        case "pints": String(localized: "pints", bundle: bundle, comment: "Unit: pints")
        case "bag": String(localized: "bag", bundle: bundle, comment: "Unit: bag")
        case "loaf": String(localized: "loaf", bundle: bundle, comment: "Unit: loaf of bread")
        case "punnet": String(localized: "punnet", bundle: bundle, comment: "Unit: punnet (small fruit box)")
        case "bulb": String(localized: "bulb", bundle: bundle, comment: "Unit: bulb of garlic")
        default: unit
        }
    }

    /// Inverse of `unitLabel`: maps a typed label back to its stored code when it names a known unit.
    /// In Polish "bag", "pack" and "punnet" all display as "opak.", so typing "opak." always stores
    /// "pack" (the first match); an untouched "bag" or "punnet" item keeps its code.
    static func unitCode(forLabel label: String) -> String {
        let known = unitSuggestions + ["pints", "bag", "loaf", "punnet", "bulb"]
        return known.first { unitLabel($0) == label } ?? label
    }

    /// "2 pcs", "1.5 kg", "3 slices": the amount with its unit, in the current language.
    /// Abbreviated units ("szt.", "g") never change, but count nouns do (Polish: 1 plaster,
    /// 2 plastry, 5 plastrów, 1,5 plastra), so those units come from whole-phrase plural keys.
    static func quantityText(_ quantity: Double, unit: String, bundle: Bundle = .main, locale: Locale = .current) -> String {
        let number = quantity.formatted(.number.precision(.fractionLength(0...2)).locale(locale))
        let isWhole = quantity.rounded() == quantity && abs(quantity) < 1_000_000_000
        if isWhole {
            let count = Int(quantity)
            let phrase: String? = switch unit {
            case "slices": String(localized: "\(count) slices", bundle: bundle, locale: locale, comment: "Quantity with unit: whole number of slices")
            case "pints": String(localized: "\(count) pints", bundle: bundle, locale: locale, comment: "Quantity with unit: whole number of pints")
            case "loaf": String(localized: "\(count) loaf", bundle: bundle, locale: locale, comment: "Quantity with unit: whole number of loaves")
            case "bulb": String(localized: "\(count) bulb", bundle: bundle, locale: locale, comment: "Quantity with unit: whole number of garlic bulbs")
            default: nil
            }
            // The phrase prints the bare integer; swap in the locale-formatted number.
            if let phrase, let range = phrase.range(of: "\(count)") {
                return phrase.replacingCharacters(in: range, with: number)
            }
        } else {
            let phrase: String? = switch unit {
            case "slices": String(localized: "\(number) slices", bundle: bundle, locale: locale, comment: "Quantity with unit: fractional number of slices, e.g. 1.5")
            case "pints": String(localized: "\(number) pints", bundle: bundle, locale: locale, comment: "Quantity with unit: fractional number of pints, e.g. 1.5")
            case "loaf": String(localized: "\(number) loaf", bundle: bundle, locale: locale, comment: "Quantity with unit: fractional number of loaves, e.g. 1.5")
            case "bulb": String(localized: "\(number) bulb", bundle: bundle, locale: locale, comment: "Quantity with unit: fractional number of garlic bulbs, e.g. 1.5")
            default: nil
            }
            if let phrase { return phrase }
        }
        return "\(number) \(unitLabel(unit, bundle: bundle))"
    }

    init(now: Date = .now) {
        expiresAt = Calendar.current.date(byAdding: .day, value: 7, to: now) ?? now
    }

    init(item: FoodItem) {
        name = item.name
        category = item.category
        storage = item.storage
        quantity = item.quantity
        unit = item.unit
        expiresAt = item.expiresAt
        notes = item.notes
        barcode = item.barcode
        brand = item.brand
    }

    var trimmedName: String { name.trimmingCharacters(in: .whitespacesAndNewlines) }
    var isValid: Bool { !trimmedName.isEmpty && quantity > 0 }

    func makeItem(now: Date = .now) -> FoodItem {
        FoodItem(
            name: trimmedName,
            quantity: quantity,
            unit: unit.trimmingCharacters(in: .whitespaces).isEmpty ? "pcs" : unit,
            category: category,
            storage: storage,
            addedAt: now,
            expiresAt: expiresAt,
            barcode: barcode,
            brand: brand,
            notes: notes
        )
    }

    func apply(to item: FoodItem) {
        item.name = trimmedName
        item.quantity = quantity
        item.unit = unit.trimmingCharacters(in: .whitespaces).isEmpty ? "pcs" : unit
        item.category = category
        item.storage = storage
        item.expiresAt = expiresAt
        item.notes = notes
        item.barcode = barcode
        item.brand = brand
    }
}
