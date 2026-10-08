package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsaAlertDecoderTest {

    private val alerts = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
    private fun alert(id: String) = alerts.first { it.id == id }

    @Test
    fun `decodes the real response including loose fields`() {
        assertEquals(7, alerts.size)

        val brie = alert("FSA-PRIN-28-2026")
        assertEquals(FoodAlert.Kind.RECALL, brie.kind)
        assertEquals(LocalDate(2026, 6, 19), brie.created)
        assertEquals("Traditional Cheese Company", brie.business)
        assertEquals("https://alerts.food.gov.uk/news-alerts/alert/fsa-prin-28-2026", brie.url)
        assertTrue(brie.products.any { it.name == "Traditional Cheese Company French Brie" })
        assertEquals("10 July 2026, 16 July 2026, and 17 July 2026", brie.products.first().batches.first().useBy)

        val potNoodle = alert("FSA-AA-43-2026")
        assertEquals(FoodAlert.Kind.ALLERGY, potNoodle.kind)
        assertEquals(listOf("Milk"), potNoodle.allergens)
    }

    @Test
    fun `flags notices covering every batch`() {
        assertTrue(alert("FSA-AA-27-2026").products.first().batches.first().coversAllBatches)
        assertFalse(alert("FSA-PRIN-28-2026").products.first().batches.first().coversAllBatches)
    }

    @Test
    fun `cached model round trips`() {
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(alerts)
        assertEquals(alerts, json.decodeFromString<List<FoodAlert>>(encoded))
    }

    @Test
    fun `batch summary reads naturally`() {
        assertEquals("Batch L123 · Best before May 2027", FoodAlert.Batch(batchCode = "L123", bestBefore = "May 2027").summary)
        assertEquals("All batches · All use by dates", FoodAlert.Batch(batchCode = "All batches", useBy = "All use by dates").summary)
    }

    @Test
    fun `only allergy alerts concern allergies`() {
        assertEquals(listOf(Allergen.MILK), alert("FSA-AA-43-2026").concerns(listOf(Allergen.MILK, Allergen.EGG)))
        assertEquals(emptyList(), alert("FSA-PRIN-28-2026").concerns(listOf(Allergen.MILK)))
    }

    @Test
    fun `allergen profile covers FSA labels`() {
        assertTrue(Allergen.GLUTEN.matches("Wheat"))
        assertTrue(Allergen.NUTS.matches("Hazelnut"))
        assertFalse(Allergen.NUTS.matches("Peanuts"))
        assertEquals(Allergen.SULPHITES, Allergen.fromRaw("sulphites"))
        assertNull(Allergen.fromRaw("unicorn"))
        assertEquals("Tree nuts", Allergen.NUTS.label)
        assertEquals("Crustaceans", Allergen.CRUSTACEANS.label)
    }

    @Test
    fun `skips malformed records and tolerates loose shapes`() {
        val body = """
        {"items": [
          {"title": "No notation", "created": "2026-07-01", "type": ["http://x/def/PRIN"]},
          {"notation": "A", "title": "Unknown type", "created": "2026-07-01", "type": ["http://x/def/Alert"]},
          {"notation": "B", "title": "Bad date", "created": "July", "type": ["http://x/def/PRIN"]},
          {"notation": "C", "title": "Loose", "created": "2026-07-01",
           "type": [{"@id": "http://x/def/AA"}],
           "otherBusiness": {"commonName": "Shop"},
           "problem": [{"allergen": {"label": ["Sesame"]}, "riskStatement": "Contains sesame"}],
           "productDetails": [
             {"productName": "  ", "batchDescription": {"batchCode": "X"}},
             {"productName": "Bagel", "batchDescription": {"batchCode": "L1"}}
           ]}
        ]}
        """.trimIndent()
        val decoded = FsaAlertDecoder.decode(body)
        assertEquals(listOf("C"), decoded.map { it.id })
        val c = decoded.single()
        assertEquals(FoodAlert.Kind.ALLERGY, c.kind)
        assertEquals(listOf("Shop"), c.otherBusinesses)
        assertEquals(listOf("Sesame"), c.allergens)
        assertEquals("Contains sesame", c.risk)
        assertEquals(listOf("Bagel"), c.products.map { it.name })
        assertEquals("L1", c.products.single().batches.single().batchCode)
        assertEquals(emptyList(), FsaAlertDecoder.decode("""{"meta": {}}"""))
    }
}
