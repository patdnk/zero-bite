package com.mazesys.zerobite.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.core.notifications.PlannedNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class AlarmReminderSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val scheduler = AlarmReminderScheduler(context, context.getSharedPreferences("reminders-test", Context.MODE_PRIVATE))

    private fun planned(id: String, at: String) =
        PlannedNotification("zerobite.item.$id", id.substringBefore('.'), "Milk expires today", "Use it today or freeze it.", Instant.parse(at))

    @Test
    fun `schedules one inexact alarm per reminder at its time`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z"), planned("milk.expired", "2026-09-13T08:30:00Z")))
        val times = alarms.scheduledAlarms.map { it.triggerAtMs }.sorted()
        assertEquals(listOf(Instant.parse("2026-09-12T08:30:00Z"), Instant.parse("2026-09-13T08:30:00Z")).map { it.toEpochMilliseconds() }, times)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms.scheduledAlarms.first().getType())
    }

    @Test
    fun `a new plan replaces the old one`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z"), planned("milk.expired", "2026-09-13T08:30:00Z")))
        scheduler.reschedule(listOf(planned("eggs.soon", "2026-09-10T08:30:00Z")))
        assertEquals(listOf(Instant.parse("2026-09-10T08:30:00Z").toEpochMilliseconds()), alarms.scheduledAlarms.map { it.triggerAtMs })
        scheduler.reschedule(emptyList())
        assertEquals(0, alarms.scheduledAlarms.size)
    }

    @Test
    fun `stale alarms are cancelled even after the process restarts`() {
        scheduler.reschedule(listOf(planned("milk.today", "2026-09-12T08:30:00Z")))
        val restarted = AlarmReminderScheduler(context, context.getSharedPreferences("reminders-test", Context.MODE_PRIVATE))
        restarted.reschedule(emptyList())
        assertEquals(0, alarms.scheduledAlarms.size)
    }
}
