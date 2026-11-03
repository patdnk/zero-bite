package com.mazesys.zerobite.privacy

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mazesys.zerobite.BuildConfig
import com.mazesys.zerobite.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Usage statistics and crash reports. Off until the user agrees: nothing starts or sends before that. */
fun interface UsageDataCollection {
    fun setEnabled(enabled: Boolean)
}

/**
 * Firebase Analytics and Crashlytics. Firebase only starts once the user agrees (the manifest drops
 * its start-up provider), and turning off also throws away crash reports not yet sent.
 */
class FirebaseUsageDataCollection(private val context: Context) : UsageDataCollection {
    override fun setEnabled(enabled: Boolean) {
        // Debug builds never collect.
        if (BuildConfig.DEBUG) return
        val started = FirebaseApp.getApps(context).isNotEmpty()
        if (enabled) {
            // Null when the build has no google-services.json: no Firebase at all.
            if (!started && FirebaseApp.initializeApp(context) == null) return
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        } else if (started) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(false)
            crashlytics.deleteUnsentReports()
        }
    }
}

/** Applies the "share usage data" setting at launch and whenever it changes. */
class UsageDataSync(private val settings: SettingsRepository, private val collection: UsageDataCollection) {
    // From the saved value, not the pre-read default: a passing "off" would delete the crash report
    // of the crash that ended the last session before it could be sent.
    fun start(scope: CoroutineScope): Job = scope.launch {
        settings.saved.map { it.shareUsageData }.distinctUntilChanged().collect(collection::setEnabled)
    }
}
