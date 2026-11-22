package com.mazesys.zerobite

import android.app.Application
import com.mazesys.zerobite.reminders.Notifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class ZeroBiteApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies.live(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        dependencies.reminders.start(dependencies.appScope)
        dependencies.usageData.start(dependencies.appScope)
    }

    /**
     * Never called on a device; Robolectric calls it at test teardown. Waits (briefly) for the syncs
     * above to stop, so one mid-run can't reach a torn-down sandbox and fail the next test.
     */
    override fun onTerminate() {
        runBlocking { withTimeoutOrNull(2.seconds) { dependencies.appScope.coroutineContext[Job]?.cancelAndJoin() } }
        super.onTerminate()
    }
}
