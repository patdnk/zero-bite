package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.core.text.TextFolding
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

data class RecallMatch(
    val alert: FoodAlert,
    val product: FoodAlert.Product,
    val itemId: String,
    val itemName: String,
    val confidence: Confidence,
    val reasons: Set<Reason>,
) {
    /** Declared weakest first so `>` means "more confident". */
    enum class Confidence { POSSIBLE, LIKELY }

    enum class Reason {
        /** The item's barcode appears on the notice. */
        BARCODE,
        /** The item's name matches a recalled product. */
        NAME,
        /** The item's brand (or a brand word in its name) matches the business or product. */
        BRAND,
        /** The item's date matches a recalled batch date. */
        DATE,
        /** The notice covers every batch of the product. */
        ALL_BATCHES,
    }

    val id: String get() = id(alert.id, itemId)

    companion object {
        fun id(alertId: String, itemId: String): String = "$alertId|$itemId"
    }
}

/**
 * Decides which food alerts might concern items in the fridge. Notices rarely carry a barcode,
 * so matching is cautious: results are "likely" or "possible", never certain. Port of the iOS
 * RecallMatcher — keep them in step via testdata/recall-matching.json.
 */
class RecallMatcher(
    private val today: LocalDate,
    /** Older notices are ignored: products from them have long left the shelves. */
    private val lookbackDays: Int = 120,
) {

    fun matches(alerts: List<FoodAlert>, items: List<FoodItem>, dismissed: Set<String> = emptySet()): List<RecallMatch> {
        val cutoff = today.minus(lookbackDays, DateTimeUnit.DAY)
        val recent = alerts.filter { it.created >= cutoff }
        val results = mutableListOf<RecallMatch>()
        for (item in items) {
            if (item.isConsumed) continue
            // Keep only the best-matching product per alert for each item.
            val best = LinkedHashMap<String, RecallMatch>()
            for (alert in recent) {
                for (product in alert.products) {
                    val match = match(alert, product, item) ?: continue
                    if (match.id in dismissed) continue
                    val existing = best[alert.id]
                    if (existing != null && existing.confidence >= match.confidence) continue
                    best[alert.id] = match
                }
            }
            results += best.values
        }
        return results.sortedWith(
            compareByDescending<RecallMatch> { it.confidence }.thenByDescending { it.alert.created }
        )
    }

    fun match(alert: FoodAlert, product: FoodAlert.Product, item: FoodItem): RecallMatch? {
        val reasons = mutableSetOf<RecallMatch.Reason>()

        val barcode = item.barcode?.filter { it.isDigit() }
        if (barcode != null && barcode.length >= 8 && barcode in barcodes(product)) {
            reasons += RecallMatch.Reason.BARCODE
        }

        val productWords = words(product.name)
        val businessWords = alert.businesses.flatMap(::words).toSet() - GENERIC_BUSINESS_WORDS
        val brandWords = words(item.brand.orEmpty()).toSet()
        val itemWords = words(item.name).filter { it !in businessWords && it !in brandWords }

        if (nameMatches(itemWords, productWords)) reasons += RecallMatch.Reason.NAME

        val allItemWords = words(item.name).toSet() + brandWords
        if (businessWords.any { it in allItemWords } || (brandWords.isNotEmpty() && brandWords.any { it in productWords })) {
            reasons += RecallMatch.Reason.BRAND
        }

        val specs = product.batches.flatMap { AlertDateParser.dates(listOfNotNull(it.useBy, it.bestBefore).joinToString(" ")) }
        if (product.batches.any { it.coversAllBatches }) {
            reasons += RecallMatch.Reason.ALL_BATCHES
        } else if (specs.any { it.matches(item.expiresOn) }) {
            reasons += RecallMatch.Reason.DATE
        }
        val dateContradicts = specs.isNotEmpty() && RecallMatch.Reason.DATE !in reasons && RecallMatch.Reason.ALL_BATCHES !in reasons

        val name = RecallMatch.Reason.NAME in reasons
        val confidence = when {
            RecallMatch.Reason.BARCODE in reasons -> RecallMatch.Confidence.LIKELY
            name && RecallMatch.Reason.BRAND in reasons ->
                if (RecallMatch.Reason.DATE in reasons || RecallMatch.Reason.ALL_BATCHES in reasons) RecallMatch.Confidence.LIKELY
                else RecallMatch.Confidence.POSSIBLE
            name && !dateContradicts -> RecallMatch.Confidence.POSSIBLE
            else -> return null
        }
        return RecallMatch(alert, product, item.id, item.name, confidence, reasons)
    }

    companion object {

        /**
         * A one-word item ("Brie", "Ham") must be the product's head noun, so "Milk" doesn't match
         * "Pistachio Milk Chocolate". Longer names need every word to appear in the product name.
         */
        fun nameMatches(itemWords: List<String>, productWords: List<String>): Boolean {
            val head = productWords.lastOrNull() ?: return false
            if (itemWords.isEmpty()) return false
            if (itemWords.size == 1) return itemWords[0] == head
            val product = productWords.toSet()
            return itemWords.all { it in product }
        }

        /** Lowercased, accent-folded, singular content words, in order. */
        fun words(text: String): List<String> =
            splitAlphanumeric(TextFolding.foldDiacritics(text).lowercase())
                .filter { it !in STOP_WORDS && it.none(Char::isDigit) }
                .map(::singular)
                .map { SYNONYMS[it] ?: it }

        /** Long digit runs on the notice (product codes and barcode-style batch codes). */
        fun barcodes(product: FoodAlert.Product): Set<String> {
            val text = (listOf(product.productCode) + product.batches.map { it.batchCode }).filterNotNull().joinToString(" ")
            return text.split(Regex("""\D+""")).filter { it.length >= 8 }.toSet()
        }

        private fun splitAlphanumeric(text: String): List<String> {
            val words = mutableListOf<String>()
            val current = StringBuilder()
            for (c in text) {
                if (c.isLetterOrDigit()) {
                    current.append(c)
                } else if (current.isNotEmpty()) {
                    words += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) words += current.toString()
            return words
        }

        private fun singular(word: String): String {
            if (word.length <= 3) return word
            if (word.endsWith("ies")) return word.dropLast(3) + "y"
            if (word.endsWith("oes")) return word.dropLast(2)
            if (word.endsWith("s") && !word.endsWith("ss")) return word.dropLast(1)
            return word
        }

        val STOP_WORDS: Set<String> = setOf(
            "the", "and", "with", "of", "in", "a", "an", "for", "style", "flavour", "flavor", "flavoured",
            "pack", "x", "g", "kg", "ml", "l", "pcs", "organic", "mini", "deluxe", "family", "range", "fresh",
            "finest", "classic", "original", "selection", "everyday", "essential", "taste", "own", "brand",
        )

        val GENERIC_BUSINESS_WORDS: Set<String> = setOf(
            "ltd", "limited", "plc", "uk", "co", "company", "foods", "food", "store", "group", "international",
            "trading", "brand", "products", "inc", "llp", "fine", "traditional",
        )

        val SYNONYMS: Map<String, String> = mapOf(
            "yoghurt" to "yogurt", "yoghurts" to "yogurt",
            "courgette" to "zucchini", "aubergine" to "eggplant", "coriander" to "cilantro",
        )
    }
}
