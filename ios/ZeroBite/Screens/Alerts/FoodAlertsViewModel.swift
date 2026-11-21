//
//  FoodAlertsViewModel.swift
//  ZeroBite
//
//  Recent food alerts (UK FSA, Poland GIS), filtered to what matters to this user: recalls that may touch
//  their fridge and allergy alerts for their allergens.
//

import Foundation
import Observation

@MainActor
@Observable
final class FoodAlertsViewModel {

    enum Filter: Hashable, CaseIterable {
        case forYou, recalls, allergy, all

        var title: String {
            switch self {
            case .forYou: String(localized: "For you")
            case .recalls: String(localized: "Recalls")
            case .allergy: String(localized: "Allergy alerts")
            case .all: String(localized: "All")
            }
        }
    }

    struct Row: Identifiable {
        let alert: FoodAlert
        /// Why it's relevant: "In your fridge: Brie" or "Contains: Milk".
        let highlight: String?
        var id: String { alert.id }
    }

    var filter: Filter = .forYou

    private let recalls: RecallStore
    private let inventory: InventoryStore
    private let settings: AppSettings
    private let now: () -> Date

    init(recalls: RecallStore, inventory: InventoryStore, settings: AppSettings, now: @escaping () -> Date = { .now }) {
        self.recalls = recalls
        self.inventory = inventory
        self.settings = settings
        self.now = now
    }

    /// One attribution line for each feed the user has turned on.
    var attributionLines: [String] {
        FoodAlert.Source.allCases.filter { settings.recallSources.contains($0) }.map(\.listAttribution)
    }

    var isRefreshing: Bool { recalls.isRefreshing }
    var errorMessage: String? { recalls.lastErrorMessage }
    var hasAllergies: Bool { !settings.allergenProfile.isEmpty }

    var lastCheckedText: String {
        guard let date = recalls.lastChecked else { return String(localized: "Not checked yet") }
        return String(localized: "Checked \(date.formatted(.relative(presentation: .named)))")
    }

    var rows: [Row] {
        let matches = recalls.matches(for: inventory.activeItems, now: now())
        let matchedNames = Dictionary(grouping: matches, by: \.alert.id).mapValues { $0.map(\.itemName) }
        let allergens = settings.allergenProfile

        return recalls.alerts.compactMap { alert in
            let fridge = matchedNames[alert.id].map { String(localized: "You have: \($0.joined(separator: ", "))") }
            let concerns = alert.concerns(allergens)
            let allergy = concerns.isEmpty ? nil : String(localized: "Contains: \(concerns.map(\.label).joined(separator: ", "))")
            let highlight = fridge ?? allergy

            switch filter {
            case .forYou: guard highlight != nil else { return nil }
            case .recalls: guard alert.kind != .allergy else { return nil }
            case .allergy: guard alert.kind == .allergy else { return nil }
            case .all: break
            }
            return Row(alert: alert, highlight: highlight)
        }
    }

    var emptyTitle: String {
        switch filter {
        case .forYou: String(localized: "Nothing affects you right now")
        case .recalls: String(localized: "No recent recalls")
        case .allergy: String(localized: "No recent allergy alerts")
        case .all: String(localized: "No alerts downloaded yet")
        }
    }

    var emptyMessage: String {
        if filter == .forYou && !hasAllergies {
            return String(localized: "No recall matches any of your food. Add your allergies in Settings to see allergy alerts here too.")
        }
        if filter == .forYou {
            return String(localized: "No recall matches any of your food, and there are no allergy alerts for your allergies.")
        }
        return String(localized: "Pull down to check for new alerts.")
    }

    func refresh() async {
        await recalls.refresh(force: true)
    }
}
