package com.mazesys.zerobite.core.products

import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ProductInfo
import com.mazesys.zerobite.core.network.ZeroBiteHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Resolves a barcode to a product name and category. */
interface ProductLookup {
    suspend fun lookup(barcode: String): ProductInfo?
}

/** The free, keyless Open Food Facts API. Timeouts are configured on the injected client. */
class OpenFoodFactsClient(private val http: HttpClient) : ProductLookup {

    override suspend fun lookup(barcode: String): ProductInfo? {
        val code = barcode.filter { it.isDigit() }
        if (code.isEmpty()) return null
        val response = http.get("https://world.openfoodfacts.org/api/v2/product/$code") {
            parameter("fields", "product_name,brands,categories_tags")
            header(HttpHeaders.UserAgent, ZeroBiteHttp.USER_AGENT)
        }
        if (response.status != HttpStatusCode.OK) return null
        return parse(code, response.bodyAsText())
    }

    @Serializable
    private data class Response(val status: Int = 0, val product: Product? = null) {
        @Serializable
        data class Product(
            @SerialName("product_name") val productName: String? = null,
            val brands: String? = null,
            @SerialName("categories_tags") val categoriesTags: List<String>? = null,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(code: String, body: String): ProductInfo? {
            val decoded = json.decodeFromString<Response>(body)
            val product = decoded.product ?: return null
            val name = product.productName?.trim()
            if (decoded.status != 1 || name.isNullOrEmpty()) return null
            return ProductInfo(
                barcode = code,
                name = name,
                brand = product.brands?.split(',')?.firstOrNull()?.trim(),
                category = category(product.categoriesTags.orEmpty()),
            )
        }

        private val RULES: List<Pair<FoodCategory, List<String>>> = listOf(
            FoodCategory.DAIRY to listOf("dairy", "dairies", "milk", "cheese", "yogurt", "yoghurt", "egg", "butter", "cream"),
            FoodCategory.MEAT to listOf("meat", "poultry", "chicken", "beef", "pork", "lamb", "sausage", "bacon", "ham"),
            FoodCategory.FISH to listOf("fish", "seafood", "salmon", "tuna", "prawn", "shrimp"),
            FoodCategory.BAKERY to listOf("bread", "bakery", "pastry", "pastries", "cake", "biscuit"),
            FoodCategory.GRAINS to listOf("pasta", "rice", "cereal", "grain", "flour", "noodle"),
            FoodCategory.CONDIMENTS to listOf("sauce", "condiment", "spread", "dressing", "mayonnaise", "ketchup", "mustard"),
            FoodCategory.DRINKS to listOf("beverage", "drink", "juice", "water", "soda"),
            FoodCategory.FRUIT to listOf("fruit"),
            FoodCategory.VEGETABLES to listOf("vegetable", "salad", "legume"),
        )

        /** Maps Open Food Facts category tags (general → specific) to our coarse categories. */
        fun category(tags: List<String>): FoodCategory? {
            for (tag in tags.asReversed()) {
                val lower = tag.lowercase()
                for ((category, needles) in RULES) {
                    if (needles.any { lower.contains(it) }) return category
                }
            }
            return null
        }
    }
}
