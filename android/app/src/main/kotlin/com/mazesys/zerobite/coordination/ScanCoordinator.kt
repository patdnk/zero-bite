package com.mazesys.zerobite.coordination

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ScanResult

sealed interface ScanRoute : NavKey {
    /** Pick a source: camera, photo or typing. */
    data object Choose : ScanRoute
    data object Live : ScanRoute
    data class Review(val result: ScanResult) : ScanRoute
    data class Form(val draft: ItemDraft) : ScanRoute
}

/**
 * Drives the add-food sheet: choose source → (live scan) → review → form → done. One per sheet
 * presentation; its view model holds it, so the stack survives configuration changes and dies with the sheet.
 */
class ScanCoordinator(private val onFinished: () -> Unit) {

    val backStack: SnapshotStateList<ScanRoute> = mutableStateListOf(ScanRoute.Choose)

    fun showLiveScanner() = push(ScanRoute.Live)

    fun showReview(result: ScanResult) = push(ScanRoute.Review(result))

    fun showForm(draft: ItemDraft) = push(ScanRoute.Form(draft))

    /** A double tap must not stack the same screen twice. */
    private fun push(route: ScanRoute) {
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Saved or cancelled: close the sheet. */
    fun finish() = onFinished()
}
