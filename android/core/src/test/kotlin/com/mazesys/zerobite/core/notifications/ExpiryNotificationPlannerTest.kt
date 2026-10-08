package com.mazesys.zerobite.core.notifications

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@Serializable
private data class NotificationFixture(val timeZone: String, val now: LocalDateTime, val cases: List<Case>) {
    @Serializable data class Prefs(val enabled: Boolean, val leadDays: Int, val hour: Int, val minute: Int)
    @Serializable data class Item(val id: String, val name: String, val expiresOn: LocalDate)
    @Serializable data class Expected(val itemId: String, val suffix: String, val title: String, val body: String, val fireAt: LocalDateTime)
    @Serializable data class Case(val name: String, val preferences: Prefs, val items: List<Item>, val expected: List<Expected>)
}

class ExpiryNotificationPlannerTest {

    private val fixture = Fixtures.load<NotificationFixture>("notification-plans.json")
    private val zone = TimeZone.of(fixture.timeZone)
    private val planner = ExpiryNotificationPlanner(zone)

    @TestFactory
    fun `golden notification plans`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val p = case.preferences
            val planned = planner.plan(
                items = case.items.map { ExpiringItemSummary(it.id, it.name, it.expiresOn) },
                preferences = NotificationPreferences(p.enabled, p.leadDays, p.hour, p.minute),
                now = fixture.now.toInstant(zone),
            )
            val expected = case.expected.map {
                PlannedNotification(
                    id = "${ExpiryNotificationPlanner.ID_PREFIX}${it.itemId}.${it.suffix}",
                    itemId = it.itemId,
                    title = it.title,
                    body = it.body,
                    fireAt = it.fireAt.toInstant(zone),
                )
            }
            assertEquals(expected, planned)
        }
    }

    @Test
    fun `fire times follow local time across the clocks change`() {
        // UK clocks go back on 25 Oct 2026: 09:30 is 08:30Z before and 09:30Z after.
        val planned = planner.plan(
            item = ExpiringItemSummary("ham", "Ham", LocalDate(2026, 10, 26)),
            preferences = NotificationPreferences(enabled = true, leadDays = 3, hour = 9, minute = 30),
            now = Instant.parse("2026-10-01T00:00:00Z"),
        )
        assertEquals(
            listOf("2026-10-23T08:30:00Z", "2026-10-26T09:30:00Z", "2026-10-27T09:30:00Z").map(Instant::parse),
            planned.map { it.fireAt },
        )
    }

    @Test
    fun `limit keeps the soonest reminders`() {
        val items = (0 until 40).map { ExpiringItemSummary("item-$it", "Item $it", LocalDate(2026, 10, 1 + it % 28)) }
        val planned = planner.plan(items, NotificationPreferences(true, 3, 9, 30), fixture.now.toInstant(zone))
        assertEquals(60, planned.size)
        assertEquals(planned.sortedBy { it.fireAt }, planned)
        assertTrue(NotificationPreferences.DEFAULT.let { !it.enabled && it.leadDays == 3 && it.hour == 9 && it.minute == 0 })
    }
}
