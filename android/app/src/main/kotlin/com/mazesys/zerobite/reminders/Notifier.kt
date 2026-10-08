package com.mazesys.zerobite.reminders

import android.content.Context

interface Notifier {
    fun sendTest()
}

class AndroidNotifier(private val context: Context) : Notifier {
    override fun sendTest() = Notifications.post(
        context,
        tag = "zerobite.test",
        title = "Zero Bite reminders are on",
        body = "This is how you'll hear about food that's about to expire.",
        itemId = null,
    )
}
