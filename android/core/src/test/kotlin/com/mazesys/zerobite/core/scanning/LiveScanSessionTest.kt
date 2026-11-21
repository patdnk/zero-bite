package com.mazesys.zerobite.core.scanning

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveScanSessionTest {

    private val today = LocalDate(2026, 9, 7)

    @Test
    fun `keeps each distinct line once, in the order first seen`() {
        val session = LiveScanSession()
            .adding(listOf("TESCO", "USE BY 12 SEP 2026"), emptyList())
            .adding(listOf("USE BY 12 SEP 2026", "SEMI SKIMMED MILK"), emptyList())
        assertEquals(listOf("TESCO", "USE BY 12 SEP 2026", "SEMI SKIMMED MILK"), session.lines)
    }

    @Test
    fun `trims lines and drops ones shorter than two characters`() {
        val session = LiveScanSession().adding(listOf("  MILK  ", "A", " ", ""), emptyList())
        assertEquals(listOf("MILK"), session.lines)
    }

    @Test
    fun `the first barcode seen wins`() {
        val session = LiveScanSession()
            .adding(emptyList(), emptyList())
            .adding(emptyList(), listOf("5000119000000", "123"))
            .adding(emptyList(), listOf("999"))
        assertEquals("5000119000000", session.barcode)
    }

    @Test
    fun `adding nothing new returns an equal session`() {
        val session = LiveScanSession().adding(listOf("MILK"), listOf("5000119000000"))
        assertEquals(session, session.adding(listOf("MILK"), listOf("111")))
    }

    @Test
    fun `result parses dates like the photo path`() {
        val result = LiveScanSession().adding(listOf("SEMI SKIMMED MILK", "USE BY 12 SEP 2026"), emptyList()).result(today)
        assertEquals(LocalDate(2026, 9, 12), result.bestCandidate?.date)
        assertNull(result.barcode)
    }

    @Test
    fun `an empty session has an empty result`() {
        assertTrue(LiveScanSession().result(today).isEmpty)
    }
}
