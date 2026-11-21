//
//  AppSettings.swift
//  ZeroBite
//
//  User preferences persisted in UserDefaults. Observable so screens update live.
//

import Foundation
import Observation

@MainActor
@Observable
final class AppSettings {

    private enum Keys {
        static let hasCompletedWelcome = "hasCompletedWelcome"
        static let notificationsEnabled = "notificationsEnabled"
        static let leadDays = "notificationLeadDays"
        static let notifyHour = "notifyHour"
        static let notifyMinute = "notifyMinute"
        static let soonThresholdDays = "soonThresholdDays"
        static let dismissedNotificationTip = "dismissedNotificationTip"
        static let recallChecksEnabled = "recallChecksEnabled"
        static let recallSources = "recallSources"
        static let allergens = "allergens"
        static let shareUsageData = "shareUsageData"
    }

    private let defaults: UserDefaults

    /// First-run welcome (intro + notification opt-in) has been seen.
    var hasCompletedWelcome: Bool {
        didSet { defaults.set(hasCompletedWelcome, forKey: Keys.hasCompletedWelcome) }
    }

    var notificationsEnabled: Bool {
        didSet { defaults.set(notificationsEnabled, forKey: Keys.notificationsEnabled) }
    }

    /// Days before expiry for the first reminder.
    var leadDays: Int {
        didSet { defaults.set(leadDays, forKey: Keys.leadDays) }
    }

    var notifyHour: Int {
        didSet { defaults.set(notifyHour, forKey: Keys.notifyHour) }
    }

    var notifyMinute: Int {
        didSet { defaults.set(notifyMinute, forKey: Keys.notifyMinute) }
    }

    /// Items expiring within this many days are shown as "expiring soon".
    var soonThresholdDays: Int {
        didSet { defaults.set(soonThresholdDays, forKey: Keys.soonThresholdDays) }
    }

    /// The Fridge screen's "turn on reminders" tip was answered (turned on or "Not now").
    var dismissedNotificationTip: Bool {
        didSet { defaults.set(dismissedNotificationTip, forKey: Keys.dismissedNotificationTip) }
    }

    /// Check the official feeds for recalls and allergy alerts.
    var recallChecksEnabled: Bool {
        didSet { defaults.set(recallChecksEnabled, forKey: Keys.recallChecksEnabled) }
    }

    /// Which feeds the recall checks read. Until the user chooses, this follows the device region.
    var recallSources: Set<FoodAlert.Source> {
        didSet { defaults.set(recallSources.map(\.rawValue).sorted(), forKey: Keys.recallSources) }
    }

    /// Poland reads the Chief Sanitary Inspectorate (GIS); everywhere else the UK Food Standards Agency.
    nonisolated static func defaultRecallSources(region: String?) -> Set<FoodAlert.Source> {
        region == "PL" ? [.gis] : [.fsa]
    }

    /// The stored feed choice, or the region default when none (or none readable) is stored.
    /// Not main-actor bound, so the alert fetcher can read it from a background task.
    nonisolated static func storedRecallSources(in defaults: UserDefaults, region: String? = Locale.current.region?.identifier) -> Set<FoodAlert.Source> {
        let stored = Set((defaults.stringArray(forKey: Keys.recallSources) ?? []).compactMap(FoodAlert.Source.init(rawValue:)))
        return stored.isEmpty ? defaultRecallSources(region: region) : stored
    }

    /// Allergens the user wants allergy alerts for (FSA allergen labels, e.g. "Milk", "Peanuts").
    var allergens: [String] {
        didSet { defaults.set(allergens, forKey: Keys.allergens) }
    }

    /// Send usage statistics and crash reports through Firebase. Off until the user agrees.
    var shareUsageData: Bool {
        didSet { defaults.set(shareUsageData, forKey: Keys.shareUsageData) }
    }

    init(defaults: UserDefaults = .standard, region: String? = Locale.current.region?.identifier) {
        self.defaults = defaults
        hasCompletedWelcome = defaults.bool(forKey: Keys.hasCompletedWelcome)
        notificationsEnabled = defaults.bool(forKey: Keys.notificationsEnabled)
        leadDays = defaults.object(forKey: Keys.leadDays) as? Int ?? NotificationPreferences.default.leadDays
        notifyHour = defaults.object(forKey: Keys.notifyHour) as? Int ?? NotificationPreferences.default.hour
        notifyMinute = defaults.object(forKey: Keys.notifyMinute) as? Int ?? NotificationPreferences.default.minute
        soonThresholdDays = defaults.object(forKey: Keys.soonThresholdDays) as? Int ?? 3
        dismissedNotificationTip = defaults.bool(forKey: Keys.dismissedNotificationTip)
        recallChecksEnabled = defaults.object(forKey: Keys.recallChecksEnabled) as? Bool ?? true
        recallSources = Self.storedRecallSources(in: defaults, region: region)
        allergens = defaults.stringArray(forKey: Keys.allergens) ?? []
        shareUsageData = defaults.bool(forKey: Keys.shareUsageData)
    }

    var notificationPreferences: NotificationPreferences {
        NotificationPreferences(enabled: notificationsEnabled, leadDays: leadDays, hour: notifyHour, minute: notifyMinute)
    }

    /// The backing store, so related stores (e.g. recall dismissals) share the same suite.
    var userDefaults: UserDefaults { defaults }

    /// Isolated defaults for tests and previews.
    static func ephemeral() -> AppSettings {
        let suite = UserDefaults(suiteName: "zerobite.ephemeral.\(UUID().uuidString)")!
        suite.removePersistentDomain(forName: suite.description)
        return AppSettings(defaults: suite)
    }
}
