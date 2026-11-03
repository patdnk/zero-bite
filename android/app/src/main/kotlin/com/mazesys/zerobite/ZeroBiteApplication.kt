package com.mazesys.zerobite

import android.app.Application
import com.mazesys.zerobite.reminders.Notifications

class ZeroBiteApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies.live(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        dependencies.reminders.start(dependencies.appScope)
        dependencies.usageData.start(dependencies.appScope)
    }
}
