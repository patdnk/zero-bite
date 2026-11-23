//
//  SettingsViewModel.swift
//  ZeroBite
//

import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class SettingsViewModel {

    enum NotificationState: Equatable {
        case on
        case off
        /// The user refused at the OS level; only the Settings app can change that.
        case denied
    }

    private(set) var isUpdatingNotifications = false

    private let dependencies: AppDependencies
    private let coordinator: SettingsCoordinator

    init(dependencies: AppDependencies, coordinator: SettingsCoordinator) {
        self.dependencies = dependencies
        self.coordinator = coordinator
    }

    private var settings: AppSettings { dependencies.settings }
    private var notifications: ExpiryNotificationService { dependencies.notifications }
    private var store: InventoryStore { dependencies.inventory }

    // MARK: - Notifications

    var notificationState: NotificationState {
        if settings.notificationsEnabled && notifications.isAuthorized { return .on }
        if notifications.isDenied { return .denied }
        return .off
    }

    var notificationsEnabled: Bool { notificationState == .on }

    var leadDays: Int {
        get { settings.leadDays }
        set {
            settings.leadDays = max(1, min(14, newValue))
            resync()
        }
    }

    var notifyTime: Date {
        get {
            Calendar.current.date(from: DateComponents(hour: settings.notifyHour, minute: settings.notifyMinute)) ?? .now
        }
        set {
            let components = Calendar.current.dateComponents([.hour, .minute], from: newValue)
            settings.notifyHour = components.hour ?? 9
            settings.notifyMinute = components.minute ?? 0
            resync()
        }
    }

    var soonThresholdDays: Int {
        get { settings.soonThresholdDays }
        set {
            settings.soonThresholdDays = max(1, min(14, newValue))
            resync()
        }
    }

    func refresh() async {
        await notifications.refreshAuthorizationStatus()
    }

    // MARK: - Food safety

    var recallChecksEnabled: Bool {
        get { settings.recallChecksEnabled }
        set {
            settings.recallChecksEnabled = newValue
            if newValue { Task { await dependencies.refreshRecalls(force: true) } }
        }
    }

    /// What the "Recalls from" menu offers, mapped onto the feeds it turns on.
    enum RecallFeed: CaseIterable, Identifiable {
        case uk, poland, both

        var id: Self { self }

        var sources: Set<FoodAlert.Source> {
            switch self {
            case .uk: [.fsa]
            case .poland: [.gis]
            case .both: [.fsa, .gis]
            }
        }

        var title: String {
            switch self {
            case .uk: String(localized: "UK (FSA)")
            case .poland: String(localized: "Poland (GIS)")
            case .both: String(localized: "Both")
            }
        }
    }

    var recallFeed: RecallFeed {
        get { RecallFeed.allCases.first { $0.sources == settings.recallSources } ?? .uk }
        set {
            guard newValue.sources != settings.recallSources else { return }
            settings.recallSources = newValue.sources
            Task { await dependencies.refreshRecalls(force: true) }
        }
    }

    func isSelected(_ allergen: Allergen) -> Bool {
        settings.allergenProfile.contains(allergen)
    }

    func toggle(_ allergen: Allergen) {
        var profile = settings.allergenProfile
        if let index = profile.firstIndex(of: allergen) {
            profile.remove(at: index)
        } else {
            profile.append(allergen)
        }
        settings.allergenProfile = profile
    }

    var alertsSummary: String {
        let recalls = dependencies.recalls
        guard let checked = recalls.lastChecked else { return String(localized: "Not checked yet") }
        let count = recalls.alerts.count
        return String(localized: "\(count) in the last \(RecallStore.historyDays) days · checked \(checked.formatted(.relative(presentation: .named)))", comment: "The day count is RecallStore.historyDays (a constant, 120); translations may assume it is at least 5")
    }

    func setNotificationsEnabled(_ enabled: Bool) async {
        isUpdatingNotifications = true
        defer { isUpdatingNotifications = false }
        if enabled {
            let granted = await notifications.requestAuthorization()
            settings.notificationsEnabled = granted
            if !granted {
                coordinator.eventLoop(event: .didFindNotificationsDenied)
            }
        } else {
            settings.notificationsEnabled = false
        }
        await dependencies.syncNotifications()
    }

    func sendTestNotification() async {
        await notifications.sendTestNotification()
    }

    func openSystemSettings() {
        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    // MARK: - Privacy

    var shareUsageData: Bool {
        get { settings.shareUsageData }
        set {
            settings.shareUsageData = newValue
            dependencies.applyUsageDataChoice()
        }
    }

    // MARK: - Data

    var activeItemCount: Int { store.activeItems.count }
    var usedItemCount: Int { store.consumedItems.count }
    var recipeCount: Int { dependencies.recipes.count }

    var appVersion: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(short) (\(build))"
    }

    func loadSampleData() {
        store.loadSampleData()
    }

    func clearUsedHistory() {
        coordinator.eventLoop(event: .didTapClearUsedHistory(itemIDs: store.consumedItems.map(\.id)))
    }

    func deleteEverything() {
        coordinator.eventLoop(event: .didTapDeleteEverything)
    }

    private func resync() {
        Task { await dependencies.syncNotifications() }
    }

    func showHowItWorks() {
        coordinator.eventLoop(event: .didTapHowItWorks)
    }
}
