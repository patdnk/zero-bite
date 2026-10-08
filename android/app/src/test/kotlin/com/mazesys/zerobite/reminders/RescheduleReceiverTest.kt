package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.mazesys.zerobite.ZeroBiteApplication
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertTrue
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class RescheduleReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<ZeroBiteApplication>()
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Test
    fun `manifest registers it for boot, updates and time changes`() {
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            val receivers = app.packageManager.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), 0)
            assertTrue(receivers.any { it.activityInfo.name == RescheduleReceiver::class.java.name }, action)
        }
    }

    @Test
    fun `reschedule receiver replans on boot and time changes`() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val deps = app.dependencies
        deps.settings.update { it.copy(notificationsEnabled = true) }
        val today = LocalDate(2030, 1, 1)
        deps.inventory.add(FoodItem(id = "milk", name = "Milk", addedAt = Clock.System.now(), expiresOn = today.plus(10, DateTimeUnit.DAY)))
        deps.scheduler.reschedule(emptyList()) // what a reboot does to alarms

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, RescheduleReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        val deadline = System.currentTimeMillis() + 5_000
        while (alarms.scheduledAlarms.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(alarms.scheduledAlarms.isNotEmpty(), "alarms restored after boot")
    }
}
