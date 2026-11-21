package com.mazesys.zerobite.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.mazesys.zerobite.core.notifications.PlannedNotification

interface ReminderScheduler {
    /** Replaces every scheduled reminder with `planned`. */
    fun reschedule(planned: List<PlannedNotification>)
}

/**
 * One inexact alarm per reminder (`setAndAllowWhileIdle`: no exact-alarm permission, fine for a
 * 9am nudge). The ids it scheduled are kept so stale alarms can be cancelled after a restart.
 */
class AlarmReminderScheduler(private val context: Context, private val prefs: SharedPreferences) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun reschedule(planned: List<PlannedNotification>) {
        prefs.getStringSet(KEY, emptySet()).orEmpty().forEach(::cancel)
        planned.forEach { reminder ->
            val intent = intentFor(reminder.id)
                .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
                .putExtra(ReminderReceiver.EXTRA_BODY, reminder.body)
                .putExtra(Notifications.EXTRA_ITEM_ID, reminder.itemId)
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAt.toEpochMilliseconds(), pending)
        }
        prefs.edit().putStringSet(KEY, planned.map { it.id }.toSet()).apply()
    }

    private fun cancel(id: String) {
        val pending = PendingIntent.getBroadcast(context, 0, intentFor(id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    /** The data URI makes each reminder's PendingIntent distinct. */
    private fun intentFor(id: String) =
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("zerobite://reminder/$id"))

    private companion object {
        const val KEY = "reminders.scheduled"
    }
}
