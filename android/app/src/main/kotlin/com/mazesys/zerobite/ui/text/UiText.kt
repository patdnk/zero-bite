package com.mazesys.zerobite.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text a view model hands to the UI without resolving it, so it follows the current app
 * language (resolved at composition, after any language change).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun uiPlural(@PluralsRes id: Int, count: Int): UiText = UiText.Plural(id, count)

@Composable
@ReadOnlyComposable
fun UiText.resolve(): String {
    LocalConfiguration.current // re-resolve when the language changes
    return resolve(LocalContext.current.resources)
}

/** Resolves string resources outside composition (e.g. names written into the database). */
fun interface Strings {
    fun get(@StringRes id: Int): String
}
