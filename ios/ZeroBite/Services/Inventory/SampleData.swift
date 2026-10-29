//
//  SampleData.swift
//  ZeroBite
//
//  A realistic fridge for previews, the simulator, and "try it out". Names are inserted in the
//  current language; units stay as their stored codes (see `ItemDraft.unitLabel`).
//

import Foundation

enum SampleData {

    static func items(now: Date = .now) -> [FoodItem] {
        func day(_ offset: Int) -> Date {
            Calendar.current.date(byAdding: .day, value: offset, to: now) ?? now
        }
        return [
            FoodItem(name: String(localized: "Greek yogurt"), quantity: 500, unit: "g", category: .dairy, expiresAt: day(-1)),
            FoodItem(name: String(localized: "Spinach"), quantity: 1, unit: "bag", category: .vegetables, expiresAt: day(0)),
            FoodItem(name: String(localized: "Chicken breast"), quantity: 2, unit: "pcs", category: .meat, expiresAt: day(1)),
            FoodItem(name: String(localized: "Semi-skimmed milk"), quantity: 2, unit: "pints", category: .dairy, expiresAt: day(2)),
            FoodItem(name: String(localized: "Mushrooms"), quantity: 250, unit: "g", category: .vegetables, expiresAt: day(2)),
            FoodItem(name: String(localized: "Sourdough bread"), quantity: 1, unit: "loaf", category: .bakery, expiresAt: day(2)),
            FoodItem(name: String(localized: "Cherry tomatoes"), quantity: 1, unit: "punnet", category: .vegetables, expiresAt: day(3)),
            FoodItem(name: String(localized: "Red pepper"), quantity: 2, unit: "pcs", category: .vegetables, expiresAt: day(4)),
            FoodItem(name: String(localized: "Bacon"), quantity: 1, unit: "pack", category: .meat, expiresAt: day(5)),
            FoodItem(name: String(localized: "Lemon"), quantity: 3, unit: "pcs", category: .fruit, expiresAt: day(8)),
            FoodItem(name: String(localized: "Bananas"), quantity: 5, unit: "pcs", category: .fruit, expiresAt: day(4)),
            FoodItem(name: String(localized: "Carrots"), quantity: 6, unit: "pcs", category: .vegetables, expiresAt: day(10)),
            FoodItem(name: String(localized: "Onion"), quantity: 3, unit: "pcs", category: .vegetables, expiresAt: day(12)),
            FoodItem(name: String(localized: "Eggs"), quantity: 6, unit: "pcs", category: .dairy, expiresAt: day(12)),
            FoodItem(name: String(localized: "Cheddar cheese"), quantity: 350, unit: "g", category: .dairy, expiresAt: day(14)),
            FoodItem(name: String(localized: "Potatoes"), quantity: 1.5, unit: "kg", category: .vegetables, storage: .pantry, expiresAt: day(14)),
            FoodItem(name: String(localized: "Garlic"), quantity: 1, unit: "bulb", category: .vegetables, storage: .pantry, expiresAt: day(30)),
            FoodItem(name: String(localized: "Butter"), quantity: 250, unit: "g", category: .dairy, expiresAt: day(30)),
            FoodItem(name: String(localized: "Salmon fillets"), quantity: 2, unit: "pcs", category: .fish, storage: .freezer, expiresAt: day(60)),
            FoodItem(name: String(localized: "Basmati rice"), quantity: 1, unit: "kg", category: .grains, storage: .pantry, expiresAt: day(300)),
            FoodItem(name: String(localized: "Spaghetti"), quantity: 500, unit: "g", category: .grains, storage: .pantry, expiresAt: day(400)),
        ]
    }
}
