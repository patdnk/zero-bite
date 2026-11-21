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

    private fun listRow(href: String, date: String = "01.10.2026") =
        """<span class="date">$date</span><div class="title"><a href="$href">X</a></div>"""

    /** The smallest page the parser accepts as an alert. */
    private fun articleHtml(date: String, producer: String? = null) = """
        <article id="main-content"><h2>Ostrzeżenie publiczne dotyczące żywności: x</h2>
        <p class="event-date">$date</p>
        <h3>Szczegóły dotyczące produktu:</h3><p>Nazwa produktu: <strong>Chleb</strong></p>
        ${producer?.let { "<p>Producent: <strong>$it</strong></p>" }.orEmpty()}
        <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></article>
    """.trimIndent()

    @Test
    fun `rejects impossible dates`() {
        val row = listRow("/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-x", date = "31.02.2026")
        assertTrue(GisAlertParser.listEntries(row).isEmpty())
        assertEquals(1, GisAlertParser.listEntries(row.replace("31.02.2026", "28.02.2026")).size)
        assertNull(GisAlertParser.alert(articleHtml("31.02.2026"), "https://www.gov.pl/web/gis/x"))
        assertNotNull(GisAlertParser.alert(articleHtml("28.02.2026"), "https://www.gov.pl/web/gis/x"))
    }

    @Test
    fun `list ignores links outside gov pl`() {
        val slug = "ostrzezenie-publiczne-dotyczace-zywnosci-x"
        val html = listOf(
            "https://example.com/web/gis/$slug",
            "https://www.gov.pl.evil.example/web/gis/$slug",
            "https://www.gov.pl@evil.example/web/gis/$slug",
            "//evil.example/web/gis/$slug",
            "https://www.gov.pl/web/gis/$slug-ok",
            "/web/gis/$slug-relative",
        ).joinToString("") { listRow(it) }
        val entries = GisAlertParser.listEntries(html)
        assertEquals(listOf("GIS-$slug-ok", "GIS-$slug-relative"), entries.map { it.id })
        assertTrue(entries.all { it.url.startsWith("https://www.gov.pl/") })
    }

    @Test
    fun `alert id matches list id for escaped slugs`() {
        val slug = "ostrzezenie-publiczne-dotyczace-zywnosci-%C5%BCurek-%28test%29"
        val entry = GisAlertParser.listEntries(listRow("/web/gis/$slug?page=2")).single()
        assertEquals("GIS-$slug", entry.id)
        val alert = assertNotNull(GisAlertParser.alert(articleHtml("01.10.2026"), entry.url))
        assertEquals(entry.id, alert.id)
    }

    @Test
    fun `list row count counts every row`() {
        val html = listRow("/web/gis/ostrzezenie-publiczne-dotyczace-zywnosci-x") +
            listRow("/web/gis/ostrzezenie-publiczne-dotyczace-wyrobu-do-kontaktu-z-zywnoscia-y")
        assertEquals(2, GisAlertParser.listRowCount(html))
        assertEquals(1, GisAlertParser.listEntries(html).size)
        assertEquals(0, GisAlertParser.listRowCount("<html></html>"))
        val snapshot = Fixtures.text("gis-list.html")
        assertTrue(GisAlertParser.listRowCount(snapshot) > GisAlertParser.listEntries(snapshot).size)
    }

    @Test
    fun `business names stop before the address`() {
        fun business(value: String) =
            GisAlertParser.alert(articleHtml("01.10.2026", producer = value), "https://www.gov.pl/web/gis/x")?.business
        assertEquals("Mlecz Ser Górskie Sp. z o. o.", business("Mlecz Ser Górskie Sp. z o. o. ul. ks. Juliana Łyska 7A"))
        assertEquals("Piekarnia Kowalski", business("Piekarnia Kowalski al. Jana Pawła 3, 00-001 Warszawa"))
        assertEquals("Mleczarnia Wieś Sp. z o.o.", business("Mleczarnia Wieś Sp. z o.o. os. Słoneczne 1"))
        assertEquals("Hurtownia", business("Hurtownia pl. Wolności 2"))
        assertEquals("Zakład Mięsny", business("Zakład Mięsny 34-600 Limanowa"))
        assertEquals("Zakład Mięsny Sp. z o.o.", business("Zakład Mięsny Sp. z o.o., ul. Długa 1"))
        assertEquals("Firma Nr 12-34 Sp. j.", business("Firma Nr 12-34 Sp. j."))
    }

    @Test
    fun `source follows id prefix`() {
        val gis = FoodAlert(id = "GIS-x", title = "t", kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 8, 1))
        assertEquals(FoodAlert.Source.GIS, gis.source)
        val fsa = FsaAlertDecoder.decode(Fixtures.text("fsa-alerts.json"))
        assertTrue(fsa.isNotEmpty() && fsa.all { it.source == FoodAlert.Source.FSA })
    }
}
