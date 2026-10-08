package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.Fixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FsaAlertsClientTest {

    private fun client(handler: MockRequestHandler) = FsaAlertsClient(HttpClient(MockEngine(handler)))

    @Test
    fun `fetches with min-created and decodes the feed`() = runTest {
        val alerts = client { request ->
            val params = request.url.parameters
            assertEquals("/food-alerts/id.json", request.url.encodedPath)
            assertEquals("2026-03-14", params["min-created"])
            assertNull(params["since"]) // the FSA API rejects "since" with HTTP 400
            assertEquals("full", params["_view"])
            assertEquals("-created", params["_sort"])
            assertEquals("200", params["_limit"])
            assertEquals("application/json", request.headers[HttpHeaders.Accept])
            respond(Fixtures.text("fsa-alerts.json"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }.fetchAlerts(since = LocalDate(2026, 3, 14))
        assertEquals(7, alerts.size)
    }

    @Test
    fun `non-200 responses throw`() = runTest {
        val error = assertFailsWith<FoodAlertsUnavailable> {
            client { respond("", HttpStatusCode.BadRequest) }.fetchAlerts(LocalDate(2026, 3, 14))
        }
        assertEquals(400, error.status)
    }

    @Test
    fun `connection failures propagate to the caller`() = runTest {
        assertFailsWith<IllegalStateException> {
            client { error("offline") }.fetchAlerts(LocalDate(2026, 3, 14))
        }
    }
}
