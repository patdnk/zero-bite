package com.mazesys.zerobite.core.scanning

/** Picks the line of OCR'd label text most likely to be the product name. */
object ProductNameGuesser {

    /** Lines containing any of these are label boilerplate, not a product name. */
    val BLOCKLIST: List<String> = listOf(
        "ingredients", "nutrition", "storage", "keep refrigerated", "keep chilled", "refrigerate",
        "www", "http", "kcal", "kj", "per 100", "typical values", "allergen", "contains", "recycle",
        "barcode", "batch", "lot ", "net weight", "energy", "protein", "carbohydrate", "fat ",
        "salt", "sugars", "fibre", "serving", "once opened", "made in", "produced", "packed",
        "ltd", "plc", "gmbh", "inc.", "©", "®", "tesco", "sainsbury", "asda", "aldi", "lidl",
        "morrisons", "waitrose", "co-op", "m&s", "marks & spencer", "ocado", "iceland",
    )

    fun guess(lines: List<String>): String? {
        var bestScore = 0.0
        var bestText: String? = null

        for (raw in lines) {
            val text = raw.trim()
            if (text.length !in 3..40) continue
            val lower = text.lowercase()
            if (BLOCKLIST.any { lower.contains(it) }) continue
            if (ExpiryDateParser.containsKeyword(text)) continue

            val letters = text.count { it.isLetter() }
            val digits = text.count { it.isDigit() }
            if (letters < 3 || digits > 2 || letters.toDouble() / text.length < 0.6) continue

            val words = text.split(' ').count { it.isNotEmpty() }
            var score = letters + minOf(words, 4) * 2.0
            if (text == text.uppercase()) score += 3 // pack fronts shout the product name

            if (bestText == null || score > bestScore) {
                bestScore = score
                bestText = text
            }
        }
        return bestText?.let(::prettify)
    }

    fun prettify(text: String): String =
        text.lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
