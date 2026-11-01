package com.mazesys.zerobite.data

import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's current language code ("en", "pl"): the per-app language on Android 13+, else the
 * system's. Updated by MainActivity, which is recreated when the language changes, so data
 * held by long-lived view models (the recipe catalogue) can follow it.
 */
class AppLanguage(initial: String = "en") {
    private val state = MutableStateFlow(initial)
    val code: StateFlow<String> = state.asStateFlow()

    fun update(configuration: Configuration) {
        state.value = of(configuration)
    }

    companion object {
        /** The first of the user's languages the app is translated into, like the resources Android picks. */
        fun of(configuration: Configuration): String =
            configuration.locales.getFirstMatch(arrayOf("en", "pl"))?.language ?: "en"
    }
}
