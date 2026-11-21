package com.mazesys.zerobite.core.products

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ProductInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class OpenFoodFactsClientTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private fun client(handler: MockRequestHandler) = OpenFoodFactsClient(HttpClient(MockEngine(handler)))

    @Test
    fun `resolves name, first brand and category`() = runTest {
        var requested = ""
        val lookup = client { request ->
            requested = request.url.toString()
            assertEquals("ZeroBite-Android/1.0", request.headers[HttpHeaders.UserAgent])
            respond(
                """{"code":"5000","status":1,"product":{"product_name":" Semi Skimmed Milk ","brands":"Tesco, Tesco Finest","categories_tags":["en:dairies","en:milks"]}}""",
                HttpStatusCode.OK, json,
            )
        }
        assertEquals(
            ProductInfo("5000112637922", "Semi Skimmed Milk", "Tesco", FoodCategory.DAIRY),
            lookup.lookup("5000112637922"),
        )
        assertEquals("https://world.openfoodfacts.org/api/v2/product/5000112637922?fields=product_name%2Cbrands%2Ccategories_tags", requested)
    }

    @Test
    fun `strips non-digits from the barcode`() = runTest {
        var path = ""
        client { request ->
            path = request.url.encodedPath
            respond("""{"status":0}""", HttpStatusCode.OK, json)
        }.lookup(" 50 0011-2637922x ")
        assertEquals("/api/v2/product/5000112637922", path)
    }

    @Test
    fun `empty barcode makes no request`() = runTest {
        assertNull(client { fail("no request expected") }.lookup(" abc "))
    }

    @Test
    fun `unknown product or HTTP error is null`() = runTest {
        assertNull(client { respond("""{"status":0,"status_verbose":"product not found"}""", HttpStatusCode.OK, json) }.lookup("123"))
        assertNull(client { respond("""{"status":1,"product":{"product_name":"  "}}""", HttpStatusCode.OK, json) }.lookup("123"))
        assertNull(client { respond("", HttpStatusCode.NotFound) }.lookup("123"))
    }

    @Test
    fun `null fields still resolve the product`() {
        assertEquals(
            ProductInfo("123", "Oat Milk", null, null),
            OpenFoodFactsClient.parse("123", """{"status":1,"product":{"product_name":"Oat Milk","brands":null,"categories_tags":null}}"""),
        )
    }

    @Test
    fun `category comes from the most specific matching tag`() {
        assertEquals(FoodCategory.DAIRY, OpenFoodFactsClient.category(listOf("en:beverages", "en:milks")))
        assertEquals(FoodCategory.FRUIT, OpenFoodFactsClient.category(listOf("en:plant-based-foods", "en:fruits")))
        assertEquals(FoodCategory.GRAINS, OpenFoodFactsClient.category(listOf("en:pastas")))
        assertNull(OpenFoodFactsClient.category(emptyList()))
        assertNull(OpenFoodFactsClient.category(listOf("en:snacks")))
    }
}
