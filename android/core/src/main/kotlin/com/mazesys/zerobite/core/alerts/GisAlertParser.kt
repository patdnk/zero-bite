package com.mazesys.zerobite.core.alerts

import com.mazesys.zerobite.core.model.FoodAlert
import com.mazesys.zerobite.core.text.TextFolding
import io.ktor.http.Url
import kotlinx.datetime.LocalDate

/**
 * Reads Poland's Chief Sanitary Inspectorate (GIS) food warnings from gov.pl HTML into the same
 * [FoodAlert] the FSA feed produces. gov.pl has no API, so this works on two kinds of page: the
 * paged list of warnings (`/web/gis/ostrzezenia?page=N`) and one article per warning. Articles
 * are loosely formatted ("Label: value" lines under headings), so the parser flattens the HTML
 * to lines and reads fields by label, never by markup. A port of the iOS `GISAlertParser`; both
 * are checked against testdata/gis-alerts.json.
 *
 * Text from gov.pl, Główny Inspektorat Sanitarny, CC BY-SA 4.0. Photos are CC BY-NC-ND and are
 * never read or fetched.
 */
object GisAlertParser {

    const val BASE = "https://www.gov.pl"

    /** One row of the list page: enough to decide whether the article is new and worth fetching. */
    data class ListEntry(val id: String, val url: String, val published: LocalDate)

    // region List page

    private const val DATE_MARKER = "<span class=\"date\">"
    private val LIST_DATE = Regex("""^\s*(\d{2})\.(\d{2})\.(\d{4})\s*</span>""")
    private val LIST_LINK = Regex("""<div class="title">\s*<a href="([^"]+)"""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * How many rows the list page has, food or not. An empty page is one with no rows at all; a
     * page of only food-contact items is still a list, so paging must go on.
     */
    fun listRowCount(html: String): Int = html.split(DATE_MARKER).size - 1

    /**
     * The food warnings on a list page, in page order (newest first). Food-contact items, which
     * GIS mixes into the same list, are skipped, and so is any link that leaves gov.pl.
     */
    fun listEntries(html: String): List<ListEntry> =
        // Each entry is `<span class="date">DD.MM.YYYY</span>` then `<div class="title"><a href=…>`.
        html.split(DATE_MARKER).drop(1).mapNotNull { chunk ->
            val day = LIST_DATE.find(chunk)?.destructured?.let { (d, m, y) -> date(d, m, y) } ?: return@mapNotNull null
            val link = LIST_LINK.find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
            val slug = pathSlug(link) ?: return@mapNotNull null
            if (!isFood(slug)) return@mapNotNull null
            val url = absolute(link)
            if (!isGovPl(url)) return@mapNotNull null
            ListEntry(id = "GIS-$slug", url = url, published = day)
        }

    /**
     * The last path segment of a link as written, without query or fragment and without decoding
     * percent escapes. List entries and articles both take their id from this.
     */
    private fun pathSlug(link: String): String? =
        link.substringBefore('?').substringBefore('#').nonEmptySplit('/').lastOrNull()

    private fun isGovPl(url: String): Boolean =
        runCatching { Url(url).host }.getOrNull() == Url(BASE).host

    private fun isFood(slug: String): Boolean =
        (slug.startsWith("ostrzezenie-publiczne-dotyczace-zywnosci") || slug.startsWith("aktualizacja-ostrzezenia-publicznego")) &&
            slug.contains("zywnosci") &&
            // "…wyrobu-do-kontaktu-z-zywnoscia" also contains "zywnosci".
            !slug.contains("kontaktu")

    private fun absolute(link: String): String = when {
        link.startsWith("http://") || link.startsWith("https://") -> link
        link.startsWith("//") -> "https:$link"
        link.startsWith("/") -> BASE + link
        else -> "$BASE/$link"
    }

    // endregion

    // region Article page

    private val ARTICLE = Regex("""<article[^>]*id="main-content"[^>]*>(.*?)</article>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE_AND_BODY = Regex("""<h2[^>]*>(.*?)</h2>(.*)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ARTICLE_DATE = Regex("""\b(\d{2})\.(\d{2})\.(\d{4})\b""")

    /**
     * Parses one warning page. Returns null when the page isn't a warning with a title, a date
     * and at least one product (an error page, a layout change).
     */
    fun alert(html: String, url: String): FoodAlert? {
        val article = ARTICLE.find(html)?.groupValues?.get(1) ?: return null
        val parts = TITLE_AND_BODY.find(article)?.groupValues ?: return null
        val title = lines(parts[1]).joinToString(" ")
        if (title.isEmpty()) return null

        // The article's date is the first one after the title: titles of updates quote the date
        // of the original warning ("z dnia 03.07.2026"), which is not this one.
        val body = lines(parts[2])
        val created = body.firstNotNullOfOrNull { line ->
            ARTICLE_DATE.find(line)?.destructured?.let { (d, m, y) -> date(d, m, y) }
        } ?: return null

        val hazard = section("Zagrożenie", body)
        val details = section("Szczegóły dotyczące produkt", body)
        val risk = hazard.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val advice = section("Zalecenia", body)

        val fields = productFields(details)
        if (fields.products.isEmpty()) return null

        val isAllergy = fold("$title ${risk.orEmpty()}").contains("alergen")
        return FoodAlert(
            id = "GIS-" + (pathSlug(url) ?: ""),
            title = title,
            shortTitle = shortTitle(title),
            kind = if (isAllergy) FoodAlert.Kind.ALLERGY else FoodAlert.Kind.RECALL,
            created = created,
            url = url,
            business = fields.business,
            otherBusinesses = fields.otherBusinesses,
            products = fields.products,
            allergens = if (isAllergy) allergens("$title ${risk.orEmpty()}") else emptyList(),
            risk = risk,
            consumerAdvice = advice.takeIf { it.isNotEmpty() }?.joinToString(" "),
        )
    }

    /**
     * "Wykrycie …" for "Ostrzeżenie publiczne dotyczące żywności: wykrycie …", with
     * "Aktualizacja: " in front for updates.
     */
    private fun shortTitle(title: String): String? {
        val colon = title.indexOf(": ")
        if (colon < 0) return null
        val rest = title.substring(colon + 2)
        if (rest.isEmpty()) return null
        val short = rest.first().uppercase() + rest.drop(1)
        return if (title.startsWith("Aktualizacja")) "Aktualizacja: $short" else short
    }

    // endregion

    // region Sections

    private val HEADINGS = listOf("Zagrożenie", "Szczegóły dotyczące produkt", "Działania", "Zalecenia", "Zdjęcia")

    /**
     * The lines under the heading that starts with `prefix`, up to the next heading. Text on the
     * heading's own line after the colon counts too.
     */
    private fun section(prefix: String, lines: List<String>): List<String> {
        val start = lines.indexOfFirst { it.startsWith(prefix) }
        if (start < 0) return emptyList()
        val result = mutableListOf<String>()
        val colon = lines[start].indexOf(':')
        if (colon >= 0) {
            val inline = lines[start].substring(colon + 1).trim(::isSpace)
            if (inline.isNotEmpty()) result += inline
        }
        for (line in lines.drop(start + 1)) {
            if (HEADINGS.any(line::startsWith)) break
            result += line
        }
        return result
    }

    // endregion

    // region Product details

    private val BUSINESS_LABELS = listOf(
        "producent", "dystrybu", "wyprodukowano dla", "wyprodukowany w ue dla", "importer",
        "sprzedawca", "sprzedaż detaliczna", "dostawca", "marka",
    )

    private class ProductFields(
        val products: List<FoodAlert.Product>,
        val business: String?,
        val otherBusinesses: List<String>,
    )

    /**
     * Reads the "Label: value" lines of the product details section. A product starts at
     * "Nazwa produktu" or "Produkt"; each batch label fills the current batch, and starts a new
     * one when it would overwrite a field already set.
     */
    private fun productFields(lines: List<String>): ProductFields {
        val products = mutableListOf<FoodAlert.Product>()
        var business: String? = null
        val others = mutableListOf<String>()

        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val label = line.substring(0, colon).trim(::isSpace).lowercase()
            val value = line.substring(colon + 1).trim(::isSpace)
            if (value.isEmpty()) continue

            if (label == "nazwa produktu" || label == "produkt") {
                products += FoodAlert.Product(name = value)
            } else if (label == "kod kreskowy") {
                if (products.isNotEmpty()) products[products.lastIndex] = products.last().copy(productCode = value)
            } else if (BUSINESS_LABELS.any(label::startsWith)) {
                val name = businessName(value)
                if (name.isEmpty()) continue
                if (label.startsWith("producent") && business == null) business = name else others += name
            } else if (products.isNotEmpty()) {
                products[products.lastIndex] = withBatchField(products.last(), label, value)
            }
        }
        return ProductFields(products, business, others)
    }

    private val ADDRESS_START = Regex(""" (?:ul|al|os|pl)\. |\b\d{2}-\d{3}\b""", RegexOption.IGNORE_CASE)

    /**
     * The company name of a "Producent: …" value. Addresses and registration numbers follow the
     * first comma, or a street (" ul. ", " al. ", " os. ", " pl. ") or a postal code.
     */
    private fun businessName(value: String): String {
        val name = value.beforeFirstComma()
        val address = ADDRESS_START.find(name)
        return (if (address != null) name.substring(0, address.range.first) else name).trim(::isSpace)
    }

    /** Text before the first comma; like Swift's `split(separator:)`, skips an empty first part. */
    private fun String.beforeFirstComma(): String {
        val comma = indexOf(',')
        if (comma < 0) return this
        return listOf(substring(0, comma), substring(comma + 1)).firstOrNull { it.isNotEmpty() } ?: this
    }

    private enum class BatchField { BATCH_CODE, BEST_BEFORE, USE_BY }

    private fun withBatchField(product: FoodAlert.Product, label: String, value: String): FoodAlert.Product {
        val fields = buildList {
            if (label.contains("partii")) add(BatchField.BATCH_CODE)
            if (label.contains("trwałości") || label.startsWith("najlepiej spożyć przed")) add(BatchField.BEST_BEFORE)
            if (label.contains("przydatności")) add(BatchField.USE_BY)
        }
        if (fields.isEmpty()) return product

        fun isSet(field: BatchField, batch: FoodAlert.Batch) = when (field) {
            BatchField.BATCH_CODE -> batch.batchCode != null
            BatchField.BEST_BEFORE -> batch.bestBefore != null
            BatchField.USE_BY -> batch.useBy != null
        }
        val batches = product.batches.toMutableList()
        if (batches.isEmpty() || fields.any { isSet(it, batches.last()) }) batches += FoodAlert.Batch()
        var batch = batches.last()
        for (field in fields) {
            batch = when (field) {
                BatchField.BATCH_CODE -> batch.copy(batchCode = value)
                BatchField.BEST_BEFORE -> batch.copy(bestBefore = value)
                BatchField.USE_BY -> batch.copy(useBy = value)
            }
        }
        batches[batches.lastIndex] = batch
        return product.copy(batches = batches)
    }

    // endregion

    // region Allergens

    /**
     * Polish word stems for each allergen, matched on folded text. Within a group every stem
     * must be present, and each must start a word ("ryb" is in "ryba" but not in "trybie").
     */
    private val ALLERGEN_STEMS: List<Pair<String, List<List<String>>>> = listOf(
        "gluten" to listOf(listOf("gluten")),
        "sulphites" to listOf(listOf("siarczyn"), listOf("dwutlenk", "siark")),
        "milk" to listOf(listOf("mlek"), listOf("laktoz")),
        "egg" to listOf(listOf("jaj")),
        "peanuts" to listOf(listOf("orzeszk", "ziemn")),
        "nuts" to listOf(listOf("orzech"), listOf("pistacj"), listOf("migdal")),
        "sesame" to listOf(listOf("sezam")),
        "soya" to listOf(listOf("soj")),
        "celery" to listOf(listOf("seler")),
        "mustard" to listOf(listOf("gorczyc")),
        "lupin" to listOf(listOf("lubin")),
        "fish" to listOf(listOf("ryb")),
        "crustaceans" to listOf(listOf("skorupiak")),
        "molluscs" to listOf(listOf("mieczak")),
    )

    /** English `Allergen` raw values named in the text, sorted. */
    private fun allergens(text: String): List<String> {
        val folded = fold(text)
        return ALLERGEN_STEMS
            .filter { (_, groups) -> groups.any { group -> group.all { startsAWord(it, folded) } } }
            .map { it.first }
            .sorted()
    }

    /** Lowercase with diacritics removed; [TextFolding] maps "ł", which has no decomposition. */
    private fun fold(text: String): String = TextFolding.foldDiacritics(text.lowercase())

    private fun startsAWord(stem: String, text: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(stem, from)
            if (at < 0) return false
            if (at == 0 || !text[at - 1].isLetterOrDigit()) return true
            from = at + stem.length
        }
    }

    // endregion

    // region HTML to lines

    private val WHITESPACE = Regex("""\s+""")
    private val BLOCK_END = Regex("""<\s*(?:br\s*/?|/p|/h2|/h3|/li|/div)\s*>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]*>""")

    /**
     * Flattens HTML to trimmed, non-empty text lines: block ends become line breaks, other tags
     * are dropped and entities decoded.
     */
    fun lines(html: String): List<String> {
        val flat = TextFolding.normalizeSpaces(html)
            .replace(WHITESPACE, " ")
            .replace(BLOCK_END, "\n")
            .replace(TAG, "")
        return decodeEntities(flat)
            .split('\n')
            .map { collapseSpaces(it).trim(::isSpace) }
            .filter { it.isNotEmpty() }
    }

    /** Non-breaking and other Unicode spaces count as spaces, as in the iOS parser. */
    private fun isSpace(c: Char): Boolean = c.isWhitespace() || c == ' '

    private fun collapseSpaces(text: String): String = buildString(text.length) {
        var inSpace = false
        for (c in text) {
            if (isSpace(c)) {
                if (!inSpace) append(' ')
                inSpace = true
            } else {
                append(c)
                inSpace = false
            }
        }
    }

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to " ", "amp" to "&", "quot" to "\"", "lt" to "<", "gt" to ">",
        "oacute" to "ó", "Oacute" to "Ó", "eacute" to "é", "ouml" to "ö", "szlig" to "ß",
        "ndash" to "–", "mdash" to "—", "bdquo" to "„", "rdquo" to "”", "ldquo" to "“",
    )

    /** Decodes in one pass, so "&amp;lt;" becomes "&lt;". Unknown entities stay as written. */
    private fun decodeEntities(text: String): String = buildString(text.length) {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '&') {
                append(c)
                i++
                continue
            }
            val semicolon = text.indexOf(';', i).takeIf { it in i until minOf(i + 12, text.length) }
            val decoded = semicolon?.let { entity(text.substring(i + 1, it)) }
            if (semicolon != null && decoded != null) {
                append(decoded)
                i = semicolon + 1
            } else {
                append('&')
                i++
            }
        }
    }

    private fun entity(name: String): String? {
        if (!name.startsWith("#")) return NAMED_ENTITIES[name]
        val digits = name.drop(1)
        val code = if (digits.startsWith("x") || digits.startsWith("X")) digits.drop(1).toIntOrNull(16) else digits.toIntOrNull()
        return code?.let(::scalar)
    }

    /** The character for a Unicode scalar value; null for surrogates and out-of-range values. */
    private fun scalar(code: Int): String? = when {
        code < 0 || code > 0x10FFFF || code in 0xD800..0xDFFF -> null
        code < 0x10000 -> code.toChar().toString()
        else -> {
            val offset = code - 0x10000
            charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
        }
    }

    // endregion

    // region Helpers

    /** A `DD.MM.YYYY` day, or null for numbers that aren't a real date (31.02.2026). */
    private fun date(day: String, month: String, year: String): LocalDate? =
        runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()

    /** Like Swift's `split(separator:)`: empty pieces are dropped. */
    private fun String.nonEmptySplit(separator: Char): List<String> = split(separator).filter { it.isNotEmpty() }

    // endregion
}
