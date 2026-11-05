package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GisAlertsClientTest {

    /**
     * Real time rather than `runTest`'s virtual clock: the client's 20 s request timeout would
     * otherwise fire as soon as the mock engine, which runs on its own dispatcher, is awaited.
     */
    private fun runLive(block: suspend CoroutineScope.() -> Unit) = runBlocking { block() }

    private val listHtml = Fixtures.text("gis-list.html")

    /** The fixture list's food entries, newest first (see gis-alerts.json `list.entries`). */
    private val entries = GisAlertParser.listEntries(listHtml)

    /** The path of an article on gov.pl: "/web/gis/<slug>". */
    private fun path(entry: GisAlertParser.ListEntry) = entry.url.removePrefix("https://www.gov.pl")

    /** The smallest page the parser accepts as an alert, dated `created` ("DD.MM.YYYY"). */
    private fun article(title: String, created: String) = """
        <article id="main-content"><h2>Ostrzeżenie publiczne dotyczące żywności: $title</h2>
        <p class="event-date">$created</p>
        <h3>Zagrożenie:</h3><p>Listeria.</p>
        <h3>Szczegóły dotyczące produktu:</h3><p>Nazwa produktu: <strong>$title</strong></p>
        <h3>Zalecenia dla konsumentów:</h3><p>Nie jeść.</p></article>
    """.trimIndent()

    /** Serves the list fixture and one synthetic article per entry, recording every request. */
    private inner class Site(
        private val createdBy: (GisAlertParser.ListEntry) -> String = { "01.10.2026" },
        private val failing: Set<String> = emptySet(),
        private val onArticle: suspend () -> Unit = {},
    ) {
        val requests = CopyOnWriteArrayList<String>()
        val listPages get() = requests.filter { it.startsWith("/web/gis/ostrzezenia") }
        val articlePaths get() = requests.filterNot { it.startsWith("/web/gis/ostrzezenia") }

        val handler: MockRequestHandler = { request ->
            val target = request.url.encodedPath + (request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
            requests += target
            assertEquals(ZeroBiteHttp.USER_AGENT, request.headers[HttpHeaders.UserAgent])
            val byPath = entries.associateBy(::path)
            when {
                request.url.encodedPath == "/web/gis/ostrzezenia" ->
                    respond(listHtml, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"))
                request.url.encodedPath in failing -> respond("", HttpStatusCode.InternalServerError)
                else -> {
                    onArticle()
                    val entry = byPath.getValue(request.url.encodedPath)
                    respond(
                        article(entry.id.removePrefix("GIS-").takeLast(20), createdBy(entry)),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
                    )
                }
            }
        }
    }

    private fun client(site: Site, maxPages: Int = 8) =
        GisAlertsClient(HttpClient(MockEngine(site.handler)), maxPages)

    @Test
    fun `gis client stops paging at the cutoff`() = runLive {
        // Page 1 reaches back to 2026-10-06, before the cutoff, so there is no page 2.
        val since = LocalDate(2026, 10, 8)
        val fresh = entries.filter { it.published >= since }
        assertEquals(5, fresh.size)
        // Created dates deliberately out of list order, to prove the result is sorted by them.
        val created = fresh.zip(listOf("10.10.2026", "20.10.2026", "15.10.2026", "12.10.2026", "18.10.2026")).toMap()
        val site = Site(createdBy = { created.getValue(it) })

        val alerts = client(site).fetchAlerts(since, known = emptyList())

        assertEquals(listOf("/web/gis/ostrzezenia?page=1"), site.listPages)
        assertEquals(fresh.map(::path).sorted(), site.articlePaths.sorted())
        assertEquals(
            listOf(fresh[1], fresh[4], fresh[2], fresh[3], fresh[0]).map { it.id },
            alerts.map { it.id },
        )
        assertEquals(
            listOf(LocalDate(2026, 10, 20), LocalDate(2026, 10, 18), LocalDate(2026, 10, 15), LocalDate(2026, 10, 12), LocalDate(2026, 10, 10)),
            alerts.map { it.created },
        )
    }

    @Test
    fun `gis client keeps paging until a page reaches past the cutoff, once per article`() = runLive {
        // Every page is the same fixture (all entries newer than the cutoff), so paging runs to
        // the cap; entries repeated on later pages are fetched once.
        val site = Site()
        val alerts = client(site, maxPages = 3).fetchAlerts(LocalDate(2026, 1, 1), emptyList())

        assertEquals((1..3).map { "/web/gis/ostrzezenia?page=$it" }, site.listPages)
        assertEquals(entries.size, site.articlePaths.size)
        assertEquals(entries.size, site.articlePaths.toSet().size)
        assertEquals(entries.map { it.id }.sorted(), alerts.map { it.id }.sorted())
    }

    @Test
    fun `gis client reuses known articles`() = runLive {
        val since = LocalDate(2026, 10, 8)
        val salami = entries[1]
        val cached = FoodAlert(id = salami.id, title = "Cached", kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 10, 19))
        // Dropped off the list and older than the window: let go. Off the list but inside it: kept.
        val gone = FoodAlert(id = "GIS-gone", title = "Gone", kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 9, 1))
        val recent = FoodAlert(id = "GIS-recent", title = "Recent", kind = FoodAlert.Kind.RECALL, created = LocalDate(2026, 10, 9))
        val site = Site()

        val alerts = client(site).fetchAlerts(since, known = listOf(cached, gone, recent))

        assertTrue(path(salami) !in site.articlePaths)
        assertEquals(4, site.articlePaths.size)
        assertTrue(cached in alerts, "the known alert is returned as it was")
        assertTrue(recent in alerts)
        assertTrue(alerts.none { it.id == "GIS-gone" })
        assertEquals(1 + 4 + 1, alerts.size)
    }

    @Test
    fun `gis client skips a broken article`() = runLive {
        val since = LocalDate(2026, 10, 8)
        val broken = entries[2]
        val site = Site(failing = setOf(path(broken)))

        val alerts = client(site).fetchAlerts(since, emptyList())

        assertTrue(path(broken) in site.articlePaths)
        assertEquals(4, alerts.size)
        assertTrue(alerts.none { it.id == broken.id })
    }

    @Test
    fun `a failing list page throws`() = runLive {
        val error = assertFailsWith<GisAlertsUnavailable> {
            GisAlertsClient(HttpClient(MockEngine { respond("", HttpStatusCode.InternalServerError) }))
                .fetchAlerts(LocalDate(2026, 10, 8), emptyList())
        }
        assertEquals(500, error.status)
    }

    @Test
    fun `gis client downloads at most three articles at once`() = runLive {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        val site = Site(onArticle = {
            peak.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
            delay(50)
            inFlight.decrementAndGet()
        })

        val alerts = client(site, maxPages = 1).fetchAlerts(LocalDate(2026, 1, 1), emptyList())

        assertEquals(entries.size, alerts.size)
        assertEquals(3, peak.get())
    }
}
