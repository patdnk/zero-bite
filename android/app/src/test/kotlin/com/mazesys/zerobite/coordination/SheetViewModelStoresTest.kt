package com.mazesys.zerobite.coordination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SheetViewModelStoresTest {

    class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val factory = viewModelFactory { initializer { Probe() } }
    private fun SheetViewModelStores.probe(id: Long) = ViewModelProvider(owner(id), factory)[Probe::class.java]

    @Test
    fun `a sheet keeps its view model across recomposition and configuration changes`() {
        val stores = SheetViewModelStores()
        val first = stores.probe(1)
        stores.retainOnly(1) // MainScreen re-runs this after an activity is recreated
        assertSame(first, stores.probe(1))
        assertFalse(first.cleared)
    }

    @Test
    fun `dismissing or replacing a sheet frees its view model`() {
        val stores = SheetViewModelStores()
        val add = stores.probe(1)
        stores.retainOnly(2) // a new sheet replaced it
        assertTrue(add.cleared)
        val edit = stores.probe(2)
        assertNotSame<ViewModel>(add, edit)
        stores.retainOnly(null) // dismissed
        assertTrue(edit.cleared)
    }

    @Test
    fun `clearing everything frees every sheet`() {
        val stores = SheetViewModelStores()
        val probe = stores.probe(7)
        stores.clearAll()
        assertTrue(probe.cleared)
    }
}
