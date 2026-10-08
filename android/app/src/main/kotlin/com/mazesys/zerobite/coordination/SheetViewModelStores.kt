package com.mazesys.zerobite.coordination

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One ViewModelStore per presented sheet. It is kept across configuration changes (the owner
 * lives in AppViewModel) and cleared as soon as that sheet is dismissed or replaced, so the
 * add/edit form keeps a half-typed draft through rotation but starts fresh every time.
 */
class SheetViewModelStores {
    private val stores = mutableMapOf<Long, ViewModelStore>()

    fun owner(sheetId: Long): ViewModelStoreOwner {
        val store = stores.getOrPut(sheetId) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    /** Frees every sheet's view models except the one still presented (`null`: none is). */
    fun retainOnly(sheetId: Long?) {
        stores.keys.filter { it != sheetId }.forEach { stores.remove(it)?.clear() }
    }

    fun clearAll() {
        stores.values.forEach(ViewModelStore::clear)
        stores.clear()
    }
}
