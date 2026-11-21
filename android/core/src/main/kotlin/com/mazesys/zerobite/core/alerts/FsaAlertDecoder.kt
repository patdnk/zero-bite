package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Decodes `GET https://data.food.gov.uk/food-alerts/id.json?_view=full…`. The API's JSON-LD is
 * loose about types — a field can be a string, an object with "@id"/"label", or an array — so
 * it is walked by hand. Records missing an id, title, date or known type are skipped.
 */
object FsaAlertDecoder {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(body: String): List<FoodAlert> {
        val items = (json.parseToJsonElement(body).jsonObject["items"] as? JsonArray) ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.let(::alert) }
    }

    private fun alert(raw: JsonObject): FoodAlert? {
        val notation = raw.string("notation") ?: return null
        val title = raw.string("title") ?: return null
        val created = raw.string("created")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val kind = raw.many("type")
            .firstNotNullOfOrNull { looseValue(it)?.substringAfterLast('/')?.let(FoodAlert.Kind::fromCode) }
            ?: return null

        val problems = raw.many("problem").filterIsInstance<JsonObject>()
        return FoodAlert(
            id = notation,
            title = title,
            shortTitle = raw.string("shortTitle"),
            kind = kind,
            created = created,
            url = raw["alertURL"]?.let(::looseValue),
            business = (raw["reportingBusiness"] as? JsonObject)?.string("commonName"),
            otherBusinesses = raw.many("otherBusiness").filterIsInstance<JsonObject>().mapNotNull { it.string("commonName") },
            products = raw.many("productDetails").filterIsInstance<JsonObject>().mapNotNull(::product),
            allergens = problems.flatMap { p -> p.many("allergen").mapNotNull(::looseLabel) }.toSet().sorted(),
            risk = problems.firstNotNullOfOrNull { it.string("riskStatement") },
            consumerAdvice = raw.string("consumerAdvice"),
        )
    }

    private fun product(raw: JsonObject): FoodAlert.Product? {
        val name = raw.string("productName")?.trim()
        if (name.isNullOrEmpty()) return null
        return FoodAlert.Product(
            name = name,
            packSize = raw.string("packSizeDescription"),
            productCode = raw.string("productCode"),
            batches = raw.many("batchDescription").filterIsInstance<JsonObject>().map {
                FoodAlert.Batch(
                    batchCode = it.string("batchCode"),
                    bestBefore = it.string("bestBeforeDescription"),
                    useBy = it.string("useByDescription"),
                    lotNumber = it.string("lotNumber"),
                )
            },
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A field that is sometimes a single value and sometimes an array of them. */
    private fun JsonObject.many(key: String): List<JsonElement> = when (val value = this[key]) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.jsonArray
        else -> listOf(value)
    }

    /** The string form: a plain string, else "@id", else "label". */
    private fun looseValue(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> element.string("@id") ?: looseLabel(element)
        else -> null
    }

    /** A plain string, or an object's "label" (itself a string or an array of strings). */
    private fun looseLabel(element: JsonElement): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> when (val label = element["label"]) {
            is JsonPrimitive -> label.takeIf { it.isString }?.content
            is JsonArray -> (label.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        }
        else -> null
    }
}
