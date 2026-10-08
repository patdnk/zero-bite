package com.mazesys.zerobite.reminders

import androidx.compose.runtime.staticCompositionLocalOf

/** Asks the system for notification permission; only ever called from an explicit user action. */
fun interface PermissionRequester {
    suspend fun requestNotifications(): Boolean
}

/** Provided by MainActivity. The default (tests, previews) never grants. */
val LocalPermissionRequester = staticCompositionLocalOf { PermissionRequester { false } }
