//
//  SampleData.swift
//  ZeroBite
//
//  A realistic fridge for previews, the simulator, and "try it out". Names are inserted in the
//  current language; units stay as their stored codes (see `ItemDraft.unitLabel`).
//

import Foundation

enum SampleData {

    /// `bundle` picks the language of the names (a specific `.lproj` in tests).
    static func items(now: Date = .now, bundle: Bundle = .main) -> [FoodItem] {
        func day(_ offset: Int) -> Date {
            Calendar.current.date(byAdding: .day, value: offset, to: now) ?? now
        }
        return [
            FoodItem(name: String(localized: "Greek yogurt", bundle: bundle), quantity: 500, unit: "g", category: .dairy, expiresAt: day(-1)),
            FoodItem(name: String(localized: "Spinach", bundle: bundle), quantity: 1, unit: "bag", category: .vegetables, expiresAt: day(0)),
            FoodItem(name: String(localized: "Chicken breast", bundle: bundle), quantity: 2, unit: "pcs", category: .meat, expiresAt: day(1)),
            FoodItem(name: String(localized: "Semi-skimmed milk", bundle: bundle), quantity: 2, unit: "pints", category: .dairy, expiresAt: day(2)),
            FoodItem(name: String(localized: "Mushrooms", bundle: bundle), quantity: 250, unit: "g", category: .vegetables, expiresAt: day(2)),
            FoodItem(name: String(localized: "Sourdough bread", bundle: bundle), quantity: 1, unit: "loaf", category: .bakery, expiresAt: day(2)),
            FoodItem(name: String(localized: "Cherry tomatoes", bundle: bundle), quantity: 1, unit: "punnet", category: .vegetables, expiresAt: day(3)),
            FoodItem(name: String(localized: "Red pepper", bundle: bundle), quantity: 2, unit: "pcs", category: .vegetables, expiresAt: day(4)),
            FoodItem(name: String(localized: "Bacon", bundle: bundle), quantity: 1, unit: "pack", category: .meat, expiresAt: day(5)),
            FoodItem(name: String(localized: "Lemon", bundle: bundle), quantity: 3, unit: "pcs", category: .fruit, expiresAt: day(8)),
            FoodItem(name: String(localized: "Bananas", bundle: bundle), quantity: 5, unit: "pcs", category: .fruit, expiresAt: day(4)),
            FoodItem(name: String(localized: "Carrots", bundle: bundle), quantity: 6, unit: "pcs", category: .vegetables, expiresAt: day(10)),
            FoodItem(name: String(localized: "Onion", bundle: bundle), quantity: 3, unit: "pcs", category: .vegetables, expiresAt: day(12)),
            FoodItem(name: String(localized: "Eggs", bundle: bundle), quantity: 6, unit: "pcs", category: .dairy, expiresAt: day(12)),
            FoodItem(name: String(localized: "Cheddar cheese", bundle: bundle), quantity: 350, unit: "g", category: .dairy, expiresAt: day(14)),
            FoodItem(name: String(localized: "Potatoes", bundle: bundle), quantity: 1.5, unit: "kg", category: .vegetables, storage: .pantry, expiresAt: day(14)),
            FoodItem(name: String(localized: "Garlic", bundle: bundle), quantity: 1, unit: "bulb", category: .vegetables, storage: .pantry, expiresAt: day(30)),
            FoodItem(name: String(localized: "Butter", bundle: bundle), quantity: 250, unit: "g", category: .dairy, expiresAt: day(30)),
            FoodItem(name: String(localized: "Salmon fillets", bundle: bundle), quantity: 2, unit: "pcs", category: .fish, storage: .freezer, expiresAt: day(60)),
            FoodItem(name: String(localized: "Basmati rice", bundle: bundle), quantity: 1, unit: "kg", category: .grains, storage: .pantry, expiresAt: day(300)),
            FoodItem(name: String(localized: "Spaghetti", bundle: bundle), quantity: 500, unit: "g", category: .grains, storage: .pantry, expiresAt: day(400)),
        ]
    }
}
