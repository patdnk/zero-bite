//
//  NotificationCopy.swift
//  ZeroBite
//
//  Every notification title and body, from the `Notifications` string table. The default reads
//  the app's current language; tests inject a specific `.lproj` bundle.
//

import Foundation

struct NotificationCopy: Sendable {
    var bundle: Bundle = .main
    var locale: Locale = .current

    // MARK: - Expiry reminders

    func soonTitle(name: String, leadDays: Int) -> String {
        leadDays == 1
            ? String(localized: "\(name) expires tomorrow", table: "Notifications", bundle: bundle, locale: locale,
                     comment: "Reminder title the day before an item's expiry date")
            : String(localized: "\(name) expires in \(leadDays) days", table: "Notifications", bundle: bundle, locale: locale,
                     comment: "Reminder title a few days before an item's expiry date")
    }

    var soonBody: String {
        String(localized: "Plan a meal around it — Zero Bite has recipe ideas.", table: "Notifications", bundle: bundle, locale: locale)
    }

    func todayTitle(name: String) -> String {
        String(localized: "\(name) expires today", table: "Notifications", bundle: bundle, locale: locale)
    }

    var todayBody: String {
        String(localized: "Use it today or freeze it.", table: "Notifications", bundle: bundle, locale: locale)
    }

    func expiredTitle(name: String) -> String {
        String(localized: "\(name) has expired", table: "Notifications", bundle: bundle, locale: locale)
    }

    var expiredBody: String {
        String(localized: "Check it and update your list.", table: "Notifications", bundle: bundle, locale: locale)
    }

    // MARK: - Recall notices

    func recallTitle(itemName: String, isLikely: Bool) -> String {
        isLikely
            ? String(localized: "Recall: check your \(itemName)", table: "Notifications", bundle: bundle, locale: locale,
                     comment: "Notification title: a food alert very likely covers this fridge item")
            : String(localized: "Possible recall: \(itemName)", table: "Notifications", bundle: bundle, locale: locale,
                     comment: "Notification title: a food alert may cover this fridge item")
    }

    func recallBody(alertTitle: String) -> String {
        String(localized: "\(alertTitle). Check the pack before eating it.", table: "Notifications", bundle: bundle, locale: locale,
               comment: "Recall notification body; the placeholder is the FSA alert title, in English")
    }

    // MARK: - Test notification

    var testTitle: String {
        String(localized: "Zero Bite reminders are on", table: "Notifications", bundle: bundle, locale: locale)
    }

    var testBody: String {
        String(localized: "This is how you'll hear about food that's about to expire.", table: "Notifications", bundle: bundle, locale: locale)
    }
}
