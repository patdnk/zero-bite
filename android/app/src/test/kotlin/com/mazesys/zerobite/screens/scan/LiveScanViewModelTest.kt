package com.mazesys.zerobite.screens.scan

import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.scanning.RecognizedText
import com.mazesys.zerobite.testing.TestClock
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveScanViewModelTest {

    private var finished: ScanResult? = null
    private val vm by lazy { LiveScanViewModel(TestClock.fixed(), onFinish = { finished = it }) }

    @Test
    fun `starts empty and can't finish`() {
        assertNull(vm.state.value.bestDate)
        assertEquals(0, vm.state.value.lineCount)
        assertFalse(vm.state.value.canFinish)
        vm.finish()
        assertNull(finished)
    }

    @Test
    fun `frames build up the date, barcode and name`() {
        vm.onFrame(RecognizedText(listOf("SEMI SKIMMED MILK"), emptyList()))
        vm.onFrame(RecognizedText(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), listOf("5000119000000")))
        val ui = vm.state.value
        assertEquals(LocalDate(2026, 9, 12), ui.bestDate)
        assertEquals("5000119000000", ui.result.barcode)
        assertEquals(2, ui.lineCount)
        assertTrue(ui.canFinish)
        vm.finish()
        assertEquals(ui.result, finished)
    }

    @Test
    fun `a frame with nothing new leaves the state as it was`() {
        vm.onFrame(RecognizedText(listOf("USE BY 12 SEP 2026"), emptyList()))
        val before = vm.state.value
        vm.onFrame(RecognizedText(listOf("USE BY 12 SEP 2026", " "), emptyList()))
        assertTrue(before === vm.state.value)
    }
}
