package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(NotificationManager::class.java))

    @Before
    fun channels() = Notifications.createChannels(app)

    private fun fire() = ReminderReceiver().onReceive(
        app,
        Intent(app, ReminderReceiver::class.java)
            .setData(Uri.parse("zerobite://reminder/zerobite.item.milk.today"))
            .putExtra(ReminderReceiver.EXTRA_TITLE, "Milk expires today")
            .putExtra(ReminderReceiver.EXTRA_BODY, "Use it today or freeze it.")
            .putExtra(Notifications.EXTRA_ITEM_ID, "milk"),
    )

    @Test
    fun `posts the reminder on the expiry channel`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        fire()
        val posted = manager.allNotifications.single()
        assertEquals("Milk expires today", posted.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Use it today or freeze it.", posted.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notifications.CHANNEL_EXPIRY, posted.channelId)
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertEquals("milk", tap.getStringExtra(Notifications.EXTRA_ITEM_ID))
    }

    @Test
    fun `stays silent without notification permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        fire()
        assertEquals(0, manager.allNotifications.size)
    }
}
