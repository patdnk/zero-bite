package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.FoodItem
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@Serializable
private data class RecallFixture(
    val matchCases: List<MatchCase>,
    val listCases: List<ListCase>,
    val alertDates: List<AlertDateCase>,
    val nameMatches: List<NameMatch>,
    val allergens: List<AllergenCase>,
    val concerns: List<Concern>,
) {
    @Serializable data class Item(
        val id: String = "item",
        val name: String,
        val expiresOn: LocalDate,
        val brand: String? = null,
        val barcode: String? = null,
        val consumed: Boolean = false,
    )
    @Serializable data class Expect(val confidence: String, val reasons: List<String>? = null, val reasonsInclude: List<String>? = null)
    @Serializable data class MatchCase(val name: String, val today: LocalDate, val alertId: String, val productIndex: Int? = null, val item: Item, val expect: Expect? = null)
    @Serializable data class Dismissed(val alertId: String, val itemId: String)
    @Serializable data class ListCase(val name: String, val today: LocalDate, val items: List<Item>, val dismissed: List<Dismissed> = emptyList(), val firstAlertId: String? = null)
    @Serializable data class Spec(val year: Int, val month: Int, val day: Int? = null)
    @Serializable data class AlertDateCase(val text: String, val expected: List<Spec>)
    @Serializable data class NameMatch(val item: String, val product: String, val matches: Boolean)
    @Serializable data class AllergenCase(val allergen: String, val label: String, val matches: Boolean)
    @Serializable data class Concern(val alertId: String, val allergens: List<String>, val expected: List<String>)
}

class RecallMatcherTest {

    private val fixture = Fixtures.load<RecallFixture>("recall-matching.json")
    private val alerts = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
    private val stamp = Instant.parse("2026-07-05T09:00:00Z")

    private fun item(spec: RecallFixture.Item) = FoodItem(
        id = spec.id,
        name = spec.name,
        category = FoodCategory.DAIRY,
        addedAt = stamp,
        expiresOn = spec.expiresOn,
        barcode = spec.barcode,
        brand = spec.brand,
        consumedAt = if (spec.consumed) stamp else null,
    )

    /** Fixture spelling of a reason (matches the iOS raw values). */
    private fun RecallMatch.Reason.fixtureName() = when (this) {
        RecallMatch.Reason.BARCODE -> "barcode"
        RecallMatch.Reason.NAME -> "name"
        RecallMatch.Reason.BRAND -> "brand"
        RecallMatch.Reason.DATE -> "date"
        RecallMatch.Reason.ALL_BATCHES -> "allBatches"
    }

    @TestFactory
    fun `golden single matches`() = fixture.matchCases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val alert = alerts.first { it.id == case.alertId }
            val products = case.productIndex?.let { listOf(alert.products[it]) } ?: alert.products
            val matcher = RecallMatcher(case.today)
            val results = products.mapNotNull { matcher.match(alert, it, item(case.item)) }
            val expect = case.expect
            if (expect == null) {
                assertEquals(emptyList(), results)
                return@dynamicTest
            }
            val match = assertNotNull(results.firstOrNull())
            assertEquals(expect.confidence, match.confidence.name.lowercase())
            val reasons = match.reasons.map { it.fixtureName() }.toSet()
            expect.reasons?.let { assertEquals(it.toSet(), reasons) }
            expect.reasonsInclude?.let { assertTrue(reasons.containsAll(it), "reasons $reasons") }
        }
    }

    @TestFactory
    fun `golden match lists`() = fixture.listCases.map { case ->
        DynamicTest.dynamicTest(case.name) {
            val dismissed = case.dismissed.map { RecallMatch.id(it.alertId, it.itemId) }.toSet()
            val found = RecallMatcher(case.today).matches(alerts, case.items.map(::item), dismissed)
            assertEquals(case.firstAlertId, found.firstOrNull()?.alert?.id)
            if (case.firstAlertId == null) assertEquals(emptyList(), found)
        }
    }

    @TestFactory
    fun `golden alert dates`() = fixture.alertDates.map { case ->
        DynamicTest.dynamicTest(case.text) {
            assertEquals(case.expected.map { AlertDateSpec(it.year, it.month, it.day) }.toSet(), AlertDateParser.dates(case.text).toSet())
        }
    }

    @TestFactory
    fun `golden name matches`() = fixture.nameMatches.map { case ->
        DynamicTest.dynamicTest("${case.item} ~ ${case.product}") {
            assertEquals(case.matches, RecallMatcher.nameMatches(RecallMatcher.words(case.item), RecallMatcher.words(case.product)))
        }
    }

    @TestFactory
    fun `golden allergens and concerns`() =
        fixture.allergens.map { case ->
            DynamicTest.dynamicTest("${case.allergen} ~ ${case.label}") {
                assertEquals(case.matches, Allergen.fromRaw(case.allergen)!!.matches(case.label))
            }
        } + fixture.concerns.map { case ->
            DynamicTest.dynamicTest("concerns ${case.alertId}") {
                val profile = case.allergens.mapNotNull(Allergen::fromRaw)
                assertEquals(case.expected, alerts.first { it.id == case.alertId }.concerns(profile).map { it.raw })
            }
        }

    @Test
    fun `words fold accents, drop stop words and digits, and singularise`() {
        assertEquals(listOf("greek", "yogurt"), RecallMatcher.words("Greek Style Yoghurts 500g"))
        assertEquals(listOf("o", "brien", "fine", "food"), RecallMatcher.words("O’Brien Fine Foods"))
        assertEquals(listOf("creme", "brulee"), RecallMatcher.words("Crème Brûlée"))
    }

    @Test
    fun `barcodes are long digit runs from product and batch codes`() {
        val product = com.mazesys.zerobite.core.model.FoodAlert.Product(
            name = "Brie",
            productCode = "EAN 5099874213772",
            batches = listOf(com.mazesys.zerobite.core.model.FoodAlert.Batch(batchCode = "L123 / 12345678")),
        )
        assertEquals(setOf("5099874213772", "12345678"), RecallMatcher.barcodes(product))
    }
}
