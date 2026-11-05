package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shape of testdata/gis-alerts.json, the golden file shared with the iOS tests. */
@Serializable
private data class GisGolden(val list: ListSpec, val alerts: List<Case>) {
    @Serializable
    data class Entry(val id: String, val url: String, val published: String)

    @Serializable
    data class ListSpec(val file: String, val entries: List<Entry>)

    @Serializable
    data class Case(val file: String, val url: String, val expect: Expect)

    @Serializable
    data class Expect(
        val id: String,
        val title: String,
        val shortTitle: String? = null,
        val kind: String,
        val created: String,
        val url: String,
        val business: String? = null,
        val otherBusinesses: List<String>,
        val products: List<FoodAlert.Product>,
        val allergens: List<String>,
        val risk: String? = null,
        val consumerAdvice: String? = null,
    )
}

class GisAlertParserTest {

    private val golden: GisGolden = Fixtures.load("gis-alerts.json")

    @Test
    fun `list matches golden`() {
        val entries = GisAlertParser.listEntries(Fixtures.text(golden.list.file))
        val actual = entries.map { GisGolden.Entry(it.id, it.url, it.published.toString()) }
        assertEquals(golden.list.entries, actual)
        assertTrue(actual.isNotEmpty())
    }

    @Test
    fun `list skips food contact items`() {
        val html = Fixtures.text("gis-list.html")
        // The snapshot really does contain food-contact items for this to prove anything.
        assertTrue(html.contains("wyrobu-do-kontaktu"))
        val entries = GisAlertParser.listEntries(html)
        assertTrue(entries.none { it.id.contains("wyrobu-do-kontaktu") })
        assertTrue(entries.all { it.id.startsWith("GIS-") && it.url.startsWith("https://www.gov.pl/") })
    }

    @Test
    fun `alerts match golden`() {
        assertEquals(6, golden.alerts.size)
        for (case in golden.alerts) {
            val want = case.expect
            val alert = assertNotNull(GisAlertParser.alert(Fixtures.text(case.file), case.url), case.file)

            assertEquals(want.id, alert.id, case.file)
            assertEquals(want.title, alert.title, case.file)
            assertEquals(want.shortTitle, alert.shortTitle, case.file)
            assertEquals(want.kind, alert.kind.code, case.file)
            assertEquals(LocalDate.parse(want.created), alert.created, case.file)
            assertEquals(want.url, alert.url, case.file)
            assertEquals(want.business, alert.business, case.file)
            assertEquals(want.otherBusinesses, alert.otherBusinesses, case.file)
            assertEquals(want.allergens, alert.allergens, case.file)
            assertEquals(want.risk, alert.risk, case.file)
            assertEquals(want.consumerAdvice, alert.consumerAdvice, case.file)
            assertEquals(FoodAlert.Source.GIS, alert.source, case.file)

            assertEquals(want.products.size, alert.products.size, case.file)
            for ((product, wantProduct) in alert.products.zip(want.products)) {
                assertEquals(wantProduct.name, product.name, case.file)
                assertEquals(wantProduct.packSize, product.packSize, case.file)
                assertEquals(wantProduct.productCode, product.productCode, case.file)
                assertEquals(wantProduct.batches.size, product.batches.size, "${case.file} ${wantProduct.name}")
                assertEquals(wantProduct.batches, product.batches, "${case.file} ${wantProduct.name}")
            }
        }
    }

    /** Anchors for the golden file itself, so a wrong golden can't pass quietly. */
    @Test
    fun `golden has the known shapes`() {
        val alerts = golden.alerts.map { it.expect }
        val bigos = alerts.first { it.id.contains("bigosu") }
        assertEquals(1, bigos.products.size)
        assertEquals(6, bigos.products[0].batches.size)
        assertEquals(listOf("gluten"), bigos.allergens)
        val cheeses = alerts.first { it.id.contains("serow-rozne") }
        assertEquals(8, cheeses.products.size)
        val ean = alerts.first { it.id.contains("sera-podpuszczkowego") }
        assertEquals("5905784304458", ean.products.first().productCode)
    }

    @Test
    fun `parses nothing from unrelated HTML`() {
        val html = "<html><body><p>Błąd</p></body></html>"
        assertNull(GisAlertParser.alert(html, "https://www.gov.pl/web/gis/x"))
        assertTrue(GisAlertParser.listEntries(html).isEmpty())
    }

    @Test
    fun `allergen stems start words`() {
        val html = """
            <article class="article-area__article " id="main-content">
            <h2>Ostrzeżenie publiczne dotyczące żywności: niezadeklarowany alergen w chlebie</h2>
            <p class="event-date">01.08.2026</p>
            <div class="editor-content"><div><h3>Zagrożenie:</h3>
            <p>Produkt należy wycofać w trybie pilnym. Wykryto gluten.</p>
            <h3>Szczegóły dotyczące produktu:</h3>
            <p>Nazwa produktu: <strong>Chleb</strong></p>
            <p>Numer partii: <strong>1</strong></p>
            <h3>Działania podjęte przez organy:</h3>
            <p>Producent: <strong>nie ma znaczenia</strong></p>
            <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></div></div>
            </article>
        """.trimIndent()
        val alert = assertNotNull(GisAlertParser.alert(html, "https://www.gov.pl/web/gis/chleb"))
        assertEquals(FoodAlert.Kind.ALLERGY, alert.kind)
        assertEquals(listOf("gluten"), alert.allergens)
        // Text after the details section, like "Producent" in running text, is not read as a field.
        assertNull(alert.business)
    }

    @Test
    fun `decodes entities`() {
        assertEquals(
            listOf("Szczegóły „x” ł&"),
            GisAlertParser.lines("<p>Szczeg&oacute;ły&nbsp;&bdquo;x&rdquo; &#322;&amp;</p>"),
        )
        assertEquals(listOf("ł&unknown;&lt;"), GisAlertParser.lines("<p>&#x142;&unknown;&amp;lt;</p>"))
    }

    @Test
    fun `source follows id prefix`() {
        val gis = FoodAlert(id = "GIS-x", title = "t", kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 8, 1))
        assertEquals(FoodAlert.Source.GIS, gis.source)
        val fsa = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
        assertTrue(fsa.isNotEmpty() && fsa.all { it.source == FoodAlert.Source.FSA })
    }
}
