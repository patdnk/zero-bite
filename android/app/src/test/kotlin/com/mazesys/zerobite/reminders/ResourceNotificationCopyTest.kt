package com.mazesys.zerobite.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.notifications.ExpiringItemSummary
import com.mazesys.zerobite.core.notifications.ExpiryNotificationPlanner
import com.mazesys.zerobite.core.notifications.NotificationPreferences
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

@Serializable
private data class NotificationFixture(val timeZone: String, val now: LocalDateTime, val cases: List<Case>) {
    @Serializable data class Prefs(val enabled: Boolean, val leadDays: Int, val hour: Int, val minute: Int)
    @Serializable data class Item(val id: String, val name: String, val expiresOn: LocalDate)
    @Serializable data class Expected(val itemId: String, val suffix: String, val title: String, val body: String, val fireAt: LocalDateTime)
    @Serializable data class Case(val name: String, val preferences: Prefs, val items: List<Item>, val expected: List<Expected>)
}

/** Runs a golden notification fixture with the reminder copy read from the string resources. */
private fun assertFixturePlans(name: String) {
    val text = File(System.getProperty("zerobite.testdata") ?: error("run through Gradle"), name).readText()
    val fixture = Json.decodeFromString<NotificationFixture>(text)
    val zone = TimeZone.of(fixture.timeZone)
    val copy = ResourceNotificationCopy(ApplicationProvider.getApplicationContext<Context>().resources)
    val planner = ExpiryNotificationPlanner(zone, copy = copy)
    for (case in fixture.cases) {
        val p = case.preferences
        val planned = planner.plan(
            items = case.items.map { ExpiringItemSummary(it.id, it.name, it.expiresOn) },
            preferences = NotificationPreferences(p.enabled, p.leadDays, p.hour, p.minute),
            now = fixture.now.toInstant(zone),
        )
        val expected = case.expected.map { listOf("${ExpiryNotificationPlanner.ID_PREFIX}${it.itemId}.${it.suffix}", it.title, it.body, it.fireAt.toInstant(zone).toString()) }
        assertEquals(expected, planned.map { listOf(it.id, it.title, it.body, it.fireAt.toString()) }, case.name)
    }
}

@RunWith(RobolectricTestRunner::class)
class ResourceNotificationCopyTest {
    @Test
    fun `english resources match the golden english reminders`() = assertFixturePlans("notification-plans.json")
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pl")
class PolishNotificationCopyTest {
    @Test
    fun `polish resources match the golden polish reminders`() = assertFixturePlans("notification-plans-pl.json")

    @Test
    fun `every lead time from two days says dni`() {
        val copy = ResourceNotificationCopy(ApplicationProvider.getApplicationContext<Context>().resources)
        assertEquals("Mleko: termin mija jutro", copy.soonTitle("Mleko", 1))
        for (days in listOf(2, 5, 12, 22, 25)) assertEquals("Mleko: termin mija za $days dni", copy.soonTitle("Mleko", days))
    }
}
