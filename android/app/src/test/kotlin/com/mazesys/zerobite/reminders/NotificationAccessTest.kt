package com.mazesys.zerobite.reminders

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val access = AndroidNotificationAccess(app, app.getSharedPreferences("access-test", Context.MODE_PRIVATE))

    @Test
    fun `not asked, then denied, then granted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationPermission.NOT_ASKED, access.permission())
        access.markAsked()
        assertEquals(NotificationPermission.DENIED, access.permission())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationPermission.GRANTED, access.permission())
    }
}
