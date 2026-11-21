package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.Fixtures
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@Serializable
private data class ProductNameFixture(val cases: List<Case>) {
    @Serializable
    data class Case(val name: String, val lines: List<String>, val expected: String? = null)
}

class ProductNameGuesserTest {

    @TestFactory
    fun `golden product names`() = Fixtures.load<ProductNameFixture>("product-names.json").cases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            assertEquals(case.expected, ProductNameGuesser.guess(case.lines))
        }
    }

    @Test
    fun `prettify title-cases each word`() {
        assertEquals("Semi Skimmed Milk", ProductNameGuesser.prettify("SEMI  SKIMMED milk"))
    }

    @Test
    fun `analyse combines text, barcode and dates`() {
        val result = LabelScanner.analyse(
            lines = listOf("GREEK YOGURT", "USE BY 14 SEP 2026"),
            barcode = "5000000000000",
            today = LocalDate(2026, 9, 7),
        )
        assertEquals("5000000000000", result.barcode)
        assertEquals("Greek Yogurt", result.suggestedName)
        assertEquals(LocalDate(2026, 9, 14), assertNotNull(result.bestCandidate).date)
        assertFalse(result.isEmpty)
    }
}
