//
//  ExpiryNotificationPlanner.swift
//  ZeroBite
//
//  Pure planning of which local notifications an item needs. No UserNotifications
//  dependency so it is trivially unit-testable.
//

import Foundation

struct ExpiringItemSummary: Equatable, Sendable {
    let id: UUID
    let name: String
    let expiresAt: Date
}

struct PlannedNotification: Equatable, Sendable {
    let id: String
    let itemID: UUID
    let title: String
    let body: String
    let fireDate: Date
}

struct ExpiryNotificationPlanner: Sendable {

    static let identifierPrefix = "zerobite.item."

    /// iOS keeps at most 64 pending local notifications per app; leave headroom.
    var limit = 60
    var calendar: Calendar = .current
    var copy = NotificationCopy()

    func plan(items: [ExpiringItemSummary], preferences: NotificationPreferences, now: Date = .now) -> [PlannedNotification] {
        guard preferences.enabled else { return [] }
        return items
            .flatMap { plan(item: $0, preferences: preferences, now: now) }
            .sorted { $0.fireDate < $1.fireDate }
            .prefix(limit)
            .map { $0 }
    }

    func plan(item: ExpiringItemSummary, preferences: NotificationPreferences, now: Date = .now) -> [PlannedNotification] {
        let expiryDay = calendar.startOfDay(for: item.expiresAt)

        func fireTime(daysFromExpiry offset: Int) -> Date? {
            guard let day = calendar.date(byAdding: .day, value: offset, to: expiryDay) else { return nil }
            return calendar.date(bySettingHour: preferences.hour, minute: preferences.minute, second: 0, of: day)
        }

        let candidates: [(suffix: String, date: Date?, title: String, body: String)] = [
            ("soon", fireTime(daysFromExpiry: -preferences.leadDays),
             copy.soonTitle(name: item.name, leadDays: preferences.leadDays), copy.soonBody),
            ("today", fireTime(daysFromExpiry: 0),
             copy.todayTitle(name: item.name), copy.todayBody),
            ("expired", fireTime(daysFromExpiry: 1),
             copy.expiredTitle(name: item.name), copy.expiredBody),
        ]

        return candidates.compactMap { candidate in
            guard let date = candidate.date, date > now else { return nil }
            return PlannedNotification(
                id: "\(Self.identifierPrefix)\(item.id.uuidString).\(candidate.suffix)",
                itemID: item.id,
                title: candidate.title,
                body: candidate.body,
                fireDate: date
            )
        }
    }
}
