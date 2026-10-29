//
//  FoodItem.swift
//  ZeroBite
//
//  A single thing in the user's fridge / freezer / cupboard, persisted with SwiftData.
//

import Foundation
import SwiftData

enum FoodCategory: String, Codable, CaseIterable, Identifiable, Sendable {
    case dairy, meat, fish, vegetables, fruit, bakery, grains, condiments, drinks, leftovers, other

    var id: String { rawValue }

    var label: String {
        switch self {
        case .dairy: String(localized: "Dairy & eggs")
        case .meat: String(localized: "Meat")
        case .fish: String(localized: "Fish & seafood")
        case .vegetables: String(localized: "Vegetables")
        case .fruit: String(localized: "Fruit")
        case .bakery: String(localized: "Bakery")
        case .grains: String(localized: "Grains & pasta")
        case .condiments: String(localized: "Condiments")
        case .drinks: String(localized: "Drinks")
        case .leftovers: String(localized: "Leftovers")
        case .other: String(localized: "Other")
        }
    }

    var symbol: String {
        switch self {
        case .dairy: "drop.fill"
        case .meat: "fork.knife"
        case .fish: "fish.fill"
        case .vegetables: "carrot.fill"
        case .fruit: "leaf.fill"
        case .bakery: "birthday.cake.fill"
        case .grains: "basket.fill"
        case .condiments: "waterbottle.fill"
        case .drinks: "wineglass.fill"
        case .leftovers: "takeoutbag.and.cup.and.straw.fill"
        case .other: "shippingbox.fill"
        }
    }

    /// Typical shelf life once bought/opened. Used when a label has no readable date.
    var defaultShelfLifeDays: Int {
        switch self {
        case .dairy: 7
        case .meat: 3
        case .fish: 2
        case .vegetables: 7
        case .fruit: 6
        case .bakery: 4
        case .grains: 180
        case .condiments: 90
        case .drinks: 30
        case .leftovers: 3
        case .other: 14
        }
    }
}

enum StorageLocation: String, Codable, CaseIterable, Identifiable, Sendable {
    case fridge, freezer, pantry

    var id: String { rawValue }

    var label: String {
        switch self {
        case .fridge: String(localized: "Fridge")
        case .freezer: String(localized: "Freezer")
        case .pantry: String(localized: "Cupboard")
        }
    }

    var symbol: String {
        switch self {
        case .fridge: "refrigerator"
        case .freezer: "snowflake"
        case .pantry: "cabinet"
        }
    }
}

/// Lifecycle of an item relative to its expiry date. Ordered from most to least urgent.
enum ExpiryStatus: Int, CaseIterable, Comparable, Sendable {
    case expired, today, soon, fresh

    static func < (lhs: ExpiryStatus, rhs: ExpiryStatus) -> Bool { lhs.rawValue < rhs.rawValue }

    var sectionTitle: String {
        switch self {
        case .expired: String(localized: "Expired")
        case .today: String(localized: "Expires today")
        case .soon: String(localized: "Expiring soon")
        case .fresh: String(localized: "Fresh")
        }
    }

    /// Needs attention: expired, expiring today, or within the "soon" window.
    var needsAttention: Bool { self != .fresh }
}

@Model
final class FoodItem {
    @Attribute(.unique) var id: UUID
    var name: String
    var quantity: Double
    var unit: String
    var categoryRaw: String
    var storageRaw: String
    var addedAt: Date
    var expiresAt: Date
    var barcode: String?
    /// Brand from the barcode lookup; helps match product recalls.
    var brand: String?
    var notes: String
    var consumedAt: Date?

    init(
        id: UUID = UUID(),
        name: String,
        quantity: Double = 1,
        unit: String = "pcs",
        category: FoodCategory = .other,
        storage: StorageLocation = .fridge,
        addedAt: Date = .now,
        expiresAt: Date,
        barcode: String? = nil,
        brand: String? = nil,
        notes: String = "",
        consumedAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.quantity = quantity
        self.unit = unit
        self.categoryRaw = category.rawValue
        self.storageRaw = storage.rawValue
        self.addedAt = addedAt
        self.expiresAt = expiresAt
        self.barcode = barcode
        self.brand = brand
        self.notes = notes
        self.consumedAt = consumedAt
    }

    var category: FoodCategory {
        get { FoodCategory(rawValue: categoryRaw) ?? .other }
        set { categoryRaw = newValue.rawValue }
    }

    var storage: StorageLocation {
        get { StorageLocation(rawValue: storageRaw) ?? .fridge }
        set { storageRaw = newValue.rawValue }
    }

    var isConsumed: Bool { consumedAt != nil }

    var quantityText: String {
        ItemDraft.quantityText(quantity, unit: unit)
    }

    /// Whole calendar days until expiry. Negative when already expired.
    func daysUntilExpiry(from now: Date = .now, calendar: Calendar = .current) -> Int {
        let start = calendar.startOfDay(for: now)
        let end = calendar.startOfDay(for: expiresAt)
        return calendar.dateComponents([.day], from: start, to: end).day ?? 0
    }

    func status(soonThresholdDays: Int = 3, now: Date = .now, calendar: Calendar = .current) -> ExpiryStatus {
        let days = daysUntilExpiry(from: now, calendar: calendar)
        if days < 0 { return .expired }
        if days == 0 { return .today }
        if days <= soonThresholdDays { return .soon }
        return .fresh
    }

    /// "Expires in 3 days", "Expired yesterday", ...
    func expiryDescription(now: Date = .now) -> String {
        let days = daysUntilExpiry(from: now)
        switch days {
        case ..<(-1): return String(localized: "Expired \(-days) days ago")
        case -1: return String(localized: "Expired yesterday")
        case 0: return String(localized: "Expires today")
        case 1: return String(localized: "Expires tomorrow")
        default: return String(localized: "Expires in \(days) days")
        }
    }

    /// Compact label for badges: "Expired", "Today", "2d", "12 Sep".
    func shortExpiryLabel(now: Date = .now) -> String {
        let days = daysUntilExpiry(from: now)
        switch days {
        case ..<0: return String(localized: "Expired")
        case 0: return String(localized: "Today")
        case 1: return String(localized: "Tomorrow")
        case 2...14: return String(localized: "\(days)d", comment: "Compact badge: days left, e.g. 4d")
        default: return expiresAt.formatted(.dateTime.day().month(.abbreviated))
        }
    }
}
