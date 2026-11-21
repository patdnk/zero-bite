package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate

/**
 * gov.pl answered with something other than a page, didn't answer in time (`status` 0), or sent
 * a first list page with no warnings on it (a layout change or an error page served as 200).
 */
class GisAlertsUnavailable(val status: Int, message: String = "GIS food alerts returned HTTP $status") : Exception(message)

/**
 * Fetches Poland's Chief Sanitary Inspectorate (GIS) food warnings straight from gov.pl on the
 * phone, so there is no server to run. Reads the paged list of warnings newest first, stops once
 * a page reaches back past the cutoff, and downloads only the articles it doesn't already have.
 * [GisAlertParser] does the parsing. Photos are never requested.
 *
 * Text from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0.
 *
 * `maxPages` is an upper bound on list pages per refresh (10 entries each), so a layout change
 * can't loop.
 */
class GisAlertsClient(private val http: HttpClient, private val maxPages: Int = 8) : FoodAlertsFetching {

    override suspend fun fetchAlerts(since: LocalDate, known: List<FoodAlert>): List<FoodAlert> {
        val entries = listEntries(since)
        val knownIds = known.mapTo(HashSet()) { it.id }
        val fresh = entries.filter { it.published >= since && it.id !in knownIds }
        val downloaded = articles(fresh)
        // New warnings were listed and none could be read: more likely an article layout change
        // than a quiet day, and returning the old alerts as a success would hide it.
        if (fresh.isNotEmpty() && downloaded.isEmpty()) {
            throw GisAlertsUnavailable(HttpStatusCode.OK.value, "None of ${fresh.size} new GIS warnings could be read")
        }

        // Known alerts stay while they are on the list or inside the window; older ones that
        // dropped off the list are let go.
        val listed = entries.mapTo(HashSet()) { it.id }
        val kept = known.filter { it.id in listed || it.created >= since }
        return (kept + downloaded).sortedWith(compareByDescending<FoodAlert> { it.created }.thenBy { it.id })
    }

    // region List

    /**
     * Reads list pages until one contains an entry older than `since`. A failing list page
     * throws, since the result would silently miss warnings; so does a first page with no rows,
     * which can't be told apart from a layout change. A later page with no rows just ends the
     * paging; one with rows but no food warnings (only food-contact items) does not.
     */
    private suspend fun listEntries(since: LocalDate): List<GisAlertParser.ListEntry> {
        val entries = mutableListOf<GisAlertParser.ListEntry>()
        val seen = HashSet<String>()
        for (page in 1..maxPages) {
            val html = html("${GisAlertParser.BASE}/web/gis/ostrzezenia", page)
            if (GisAlertParser.listRowCount(html) == 0) {
                if (page == 1) throw GisAlertsUnavailable(HttpStatusCode.OK.value, "GIS list page 1 has no warnings")
                break
            }
            val pageEntries = GisAlertParser.listEntries(html)
            // A new warning can shift entries onto the next page between two requests.
            entries += pageEntries.filter { seen.add(it.id) }
            if (pageEntries.any { it.published < since }) break
        }
        return entries
    }

    // endregion

    // region Articles

    /**
     * Downloads and parses the articles, a few at a time. One that fails to load or parse is
     * left out so a single bad page doesn't hide the rest.
     */
    private suspend fun articles(entries: List<GisAlertParser.ListEntry>): List<FoodAlert> {
        val permits = Semaphore(MAX_CONCURRENT_ARTICLES)
        return coroutineScope {
            entries.map { entry -> async { permits.withPermit { article(entry) } } }.awaitAll().filterNotNull()
        }
    }

    private suspend fun article(entry: GisAlertParser.ListEntry): FoodAlert? {
        val page = try {
            html(entry.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return GisAlertParser.alert(page, entry.url)
    }

    // endregion

    // region Networking

    private suspend fun html(url: String, page: Int? = null): String {
        // Not the client's timeout plugin: this holds for whatever engine and config the app uses.
        val bytes = withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
            val response = http.get(url) {
                if (page != null) parameter("page", page)
                header(HttpHeaders.Accept, "text/html")
                header(HttpHeaders.UserAgent, ZeroBiteHttp.USER_AGENT)
            }
            if (response.status != HttpStatusCode.OK) throw GisAlertsUnavailable(response.status.value)
            response.bodyAsBytes()
        } ?: throw GisAlertsUnavailable(0)
        return bytes.decodeToString()
    }

    // endregion

    private companion object {
        /** Articles downloaded at once; gov.pl is a public site, so be gentle with it. */
        const val MAX_CONCURRENT_ARTICLES = 3
        const val REQUEST_TIMEOUT_MILLIS = 20_000L
    }
}
