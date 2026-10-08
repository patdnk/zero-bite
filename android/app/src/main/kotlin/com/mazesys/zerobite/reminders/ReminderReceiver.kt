package com.mazesys.zerobite.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by an alarm: shows the planned reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.data?.lastPathSegment ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        Notifications.post(context, tag, title, intent.getStringExtra(EXTRA_BODY).orEmpty(), intent.getStringExtra(Notifications.EXTRA_ITEM_ID))
    }

    companion object {
        const val EXTRA_TITLE = "com.mazesys.zerobite.extra.TITLE"
        const val EXTRA_BODY = "com.mazesys.zerobite.extra.BODY"
    }
}
