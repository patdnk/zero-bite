package com.mazesys.zerobite.reminders

import androidx.compose.runtime.staticCompositionLocalOf

/** Asks the system for notification permission; only ever called from an explicit user action. */
fun interface PermissionRequester {
    suspend fun requestNotifications(): Boolean
}

/** Provided by MainActivity. The default (tests, previews) never grants. */
val LocalPermissionRequester = staticCompositionLocalOf { PermissionRequester { false } }

/**
 * Carries a notification-permission result to whoever asked for it. Kept in AppViewModel, so
 * a result delivered to a recreated activity (dark-mode toggle, font change while the system
 * dialog is up) still completes the original request instead of leaving it waiting forever.
 */
class PermissionRelay {
    private var pending: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    suspend fun request(launch: () -> Unit): Boolean {
        val result = pending ?: kotlinx.coroutines.CompletableDeferred<Boolean>().also {
            pending = it
            launch()
        }
        return result.await()
    }

    fun deliver(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}
