package com.mazesys.zerobite.reminders

import android.content.Context
import android.content.SharedPreferences

enum class NotificationPermission {
    GRANTED,
    /** Asked before and not granted: only system settings can change it now. */
    DENIED,
    NOT_ASKED,
}

interface NotificationAccess {
    fun permission(): NotificationPermission
    /** Records that the user has been asked, so a refusal reads as DENIED rather than NOT_ASKED. */
    fun markAsked()
}

class AndroidNotificationAccess(private val context: Context, private val prefs: SharedPreferences) : NotificationAccess {
    override fun permission(): NotificationPermission = when {
        Notifications.canPost(context) -> NotificationPermission.GRANTED
        Notifications.reminderChannelBlocked(context) -> NotificationPermission.DENIED
        prefs.getBoolean(ASKED, false) -> NotificationPermission.DENIED
        else -> NotificationPermission.NOT_ASKED
    }

    override fun markAsked() {
        prefs.edit().putBoolean(ASKED, true).apply()
    }

    private companion object {
        const val ASKED = "notifications.asked"
    }
}
