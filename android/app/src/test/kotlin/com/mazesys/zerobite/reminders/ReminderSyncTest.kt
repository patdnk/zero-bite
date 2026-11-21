@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package com.mazesys.zerobite.reminders

import com.mazesys.zerobite.data.SettingsSnapshot
import com.mazesys.zerobite.testing.FakeAccess
import com.mazesys.zerobite.testing.FakeInventoryRepository
import com.mazesys.zerobite.testing.FakeScheduler
import com.mazesys.zerobite.testing.FakeSettings
import com.mazesys.zerobite.testing.TestClock
import com.mazesys.zerobite.testing.recipeFridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderSyncTest {

    private val inventory = FakeInventoryRepository(recipeFridge())
    private val settings = FakeSettings(SettingsSnapshot(notificationsEnabled = true))
    private val access = FakeAccess(NotificationPermission.GRANTED)
    private val scheduler = FakeScheduler()
    private val sync = ReminderSync(inventory, settings, TestClock.fixed(), access, scheduler)

    @Test
    fun `plans reminders for every active item and follows changes`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        sync.start(scope)
        // Eggs (tomorrow): lead reminder already past, so today + expired; butter and chicken: three each.
        assertTrue(scheduler.last.isNotEmpty())
        assertTrue(scheduler.last.all { it.id.startsWith("zerobite.item.") })
        assertEquals(setOf("eggs", "butter", "chicken"), scheduler.last.map { it.itemId }.toSet())

        inventory.markConsumed(listOf("butter", "chicken"), TestClock.now)
        assertEquals(setOf("eggs"), scheduler.last.map { it.itemId }.toSet())

        settings.update { it.copy(notificationsEnabled = false) }
        assertEquals(emptyList(), scheduler.last)
        scope.cancel()
    }

    @Test
    fun `revoked permission cancels every reminder`() = runBlocking {
        sync.resyncNow()
        assertTrue(scheduler.last.isNotEmpty())
        access.current = NotificationPermission.DENIED // switched off in system settings
        sync.resyncNow()
        assertEquals(emptyList(), scheduler.last)
    }

    @Test
    fun `reminder time and lead days come from settings`() = runBlocking {
        settings.update { it.copy(leadDays = 1, notifyHour = 7, notifyMinute = 15) }
        sync.resyncNow()
        val chickenSoon = scheduler.last.first { it.id == "zerobite.item.chicken.soon" }
        assertEquals("Chicken breast expires tomorrow", chickenSoon.title)
        // Chicken expires Sat 12 Sep; one day before at 07:15 BST.
        assertEquals(kotlin.time.Instant.parse("2026-09-11T06:15:00Z"), chickenSoon.fireAt)
    }

    @Test
    fun `replans never overlap, so no alarm is left untracked`() = runBlocking {
        val slow = object : ReminderScheduler {
            val inside = java.util.concurrent.atomic.AtomicInteger()
            var maxInside = 0
            override fun reschedule(planned: List<com.mazesys.zerobite.core.notifications.PlannedNotification>) {
                maxInside = maxOf(maxInside, inside.incrementAndGet())
                Thread.sleep(50)
                inside.decrementAndGet()
            }
        }
        val overlapping = ReminderSync(inventory, settings, TestClock.fixed(), access, slow)
        val jobs = List(4) { kotlinx.coroutines.GlobalScope.launch(Dispatchers.Default) { overlapping.resyncNow() } }
        jobs.forEach { it.join() }
        assertEquals(1, slow.maxInside)
    }
}
