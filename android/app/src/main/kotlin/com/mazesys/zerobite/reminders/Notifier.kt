package com.mazesys.zerobite.reminders

import android.content.Context
import com.mazesys.zerobite.R

interface Notifier {
    fun sendTest()
}

class AndroidNotifier(private val context: Context) : Notifier {
    override fun sendTest() = Notifications.post(
        context,
        tag = "zerobite.test",
        title = context.getString(R.string.notification_test_title),
        body = context.getString(R.string.notification_test_body),
        itemId = null,
    )
}
