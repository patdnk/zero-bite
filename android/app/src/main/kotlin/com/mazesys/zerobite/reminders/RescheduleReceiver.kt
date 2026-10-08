package com.mazesys.zerobite.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mazesys.zerobite.ZeroBiteApplication
import kotlinx.coroutines.launch

/** Android forgets alarms on reboot and app update, and times shift with the clock: replan. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = (context.applicationContext as ZeroBiteApplication).dependencies
        val pending = goAsync()
        dependencies.appScope.launch {
            try {
                dependencies.reminders.resyncNow()
            } finally {
                pending?.finish()
            }
        }
    }
}
