package com.mazesys.zerobite.core.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

data class ExpiringItemSummary(val id: String, val name: String, val expiresOn: LocalDate)

data class PlannedNotification(
    val id: String,
    val itemId: String,
    val title: String,
    val body: String,
    val fireAt: Instant,
)

data class NotificationPreferences(
    val enabled: Boolean,
    /** Days before expiry for the first heads-up. */
    val leadDays: Int,
    val hour: Int,
    val minute: Int,
) {
    companion object {
        val DEFAULT = NotificationPreferences(enabled = false, leadDays = 3, hour = 9, minute = 0)
    }
}

/**
 * Pure planning of which reminders each item needs: a heads-up `leadDays` before, one on the
 * day, one the day after. Port of the iOS ExpiryNotificationPlanner — keep them in step via
 * testdata/notification-plans.json.
 */
class ExpiryNotificationPlanner(
    private val timeZone: TimeZone,
    /** Same cap as iOS (which allows 64 pending notifications) so both platforms behave alike. */
    private val limit: Int = 60,
    /** The reminder text, in the app language. */
    private val copy: NotificationCopy = NotificationCopy.ENGLISH,
) {

    fun plan(items: List<ExpiringItemSummary>, preferences: NotificationPreferences, now: Instant): List<PlannedNotification> {
        if (!preferences.enabled) return emptyList()
        return items
            .flatMap { plan(it, preferences, now) }
            .sortedBy { it.fireAt }
            .take(limit)
    }

    fun plan(item: ExpiringItemSummary, preferences: NotificationPreferences, now: Instant): List<PlannedNotification> {
        fun fireAt(daysFromExpiry: Int): Instant =
            LocalDateTime(item.expiresOn.plus(daysFromExpiry, DateTimeUnit.DAY), LocalTime(preferences.hour, preferences.minute))
                .toInstant(timeZone)

        val candidates = listOf(
            Candidate("soon", fireAt(-preferences.leadDays), copy.soonTitle(item.name, preferences.leadDays), copy.soonBody),
            Candidate("today", fireAt(0), copy.todayTitle(item.name), copy.todayBody),
            Candidate("expired", fireAt(1), copy.expiredTitle(item.name), copy.expiredBody),
        )

        return candidates
            .filter { it.fireAt > now }
            .map { PlannedNotification("$ID_PREFIX${item.id}.${it.suffix}", item.id, it.title, it.body, it.fireAt) }
    }

    private class Candidate(val suffix: String, val fireAt: Instant, val title: String, val body: String)

    companion object {
        const val ID_PREFIX = "zerobite.item."
    }
}
