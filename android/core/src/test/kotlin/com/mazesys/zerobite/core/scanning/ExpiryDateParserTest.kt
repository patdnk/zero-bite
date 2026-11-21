package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class ExpiryDateFixture(val today: LocalDate, val cases: List<Case>) {
    @Serializable
    data class Case(
        val name: String,
        val lines: List<String>,
        val best: LocalDate? = null,
        val count: Int? = null,
        val bestSourceLine: String? = null,
    )
}

class ExpiryDateParserTest {

    private val fixture = Fixtures.load<ExpiryDateFixture>("expiry-dates.json")

    @TestFactory
    fun `golden expiry dates`() = fixture.cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val candidates = ExpiryDateParser(fixture.today).parse(case.lines)
            assertEquals(case.best, candidates.firstOrNull()?.date, "best date")
            case.count?.let { assertEquals(it, candidates.size, "candidate count") }
            case.bestSourceLine?.let { assertEquals(it, candidates.first().sourceLine, "source line") }
        }
    }

    @Test
    fun `text is split into lines`() {
        val parser = ExpiryDateParser(fixture.today)
        assertEquals(LocalDate(2026, 9, 10), parser.parse("PACKED 01/09/2026\nUSE BY 10/09/2026").first().date)
        assertEquals(LocalDate(2026, 9, 10), parser.bestDate(listOf("USE BY 10/09/2026")))
    }

    @Test
    fun `keywords are whole words`() {
        assertTrue(ExpiryDateParser.containsKeyword("Best before end"))
        assertTrue(ExpiryDateParser.containsKeyword("EXP 12/09"))
        assertFalse(ExpiryDateParser.containsKeyword("Express delivery"))
    }
}
