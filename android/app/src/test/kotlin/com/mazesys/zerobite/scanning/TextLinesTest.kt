package com.mazesys.zerobite.scanning

import org.junit.Test
import kotlin.test.assertEquals

class TextLinesTest {

    @Test
    fun `lines read top to bottom, like the iOS Vision sort`() {
        val ordered = TextLines.ordered(
            listOf(PositionedLine("USE BY 12 SEP", 300), PositionedLine("TESCO", 40), PositionedLine("MILK", 120)),
        )
        assertEquals(listOf("TESCO", "MILK", "USE BY 12 SEP"), ordered)
    }

    @Test
    fun `lines without a position go last, in reading order`() {
        val ordered = TextLines.ordered(
            listOf(PositionedLine("B", null), PositionedLine("TOP", 10), PositionedLine("C", null)),
        )
        assertEquals(listOf("TOP", "B", "C"), ordered)
    }

    @Test
    fun `blank lines are dropped and the rest trimmed`() {
        assertEquals(listOf("MILK"), TextLines.ordered(listOf(PositionedLine("  MILK ", 5), PositionedLine("   ", 6))))
    }
}
