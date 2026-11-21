package com.mazesys.zerobite.reminders

import android.content.res.Resources
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.notifications.NotificationCopy

/** Expiry reminder text from the string resources, in the language of [resources]. */
class ResourceNotificationCopy(private val resources: Resources) : NotificationCopy {
    override fun soonTitle(name: String, leadDays: Int): String =
        if (leadDays == 1) {
            resources.getString(R.string.reminder_soon_title_tomorrow, name)
        } else {
            resources.getQuantityString(R.plurals.reminder_soon_title_days, leadDays, name, leadDays)
        }

    override val soonBody: String get() = resources.getString(R.string.reminder_soon_body)

    override fun todayTitle(name: String): String = resources.getString(R.string.reminder_today_title, name)

    override val todayBody: String get() = resources.getString(R.string.reminder_today_body)

    override fun expiredTitle(name: String): String = resources.getString(R.string.reminder_expired_title, name)

    override val expiredBody: String get() = resources.getString(R.string.reminder_expired_body)
}
