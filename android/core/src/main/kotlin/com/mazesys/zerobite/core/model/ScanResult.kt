package com.mazesys.zerobite.core.model

import kotlinx.datetime.LocalDate

/** A date found on a label, with how confident the parser is that it's the expiry. */
data class DateCandidate(
    val date: LocalDate,
    val matchedText: String,
    val sourceLine: String,
    val confidence: Double,
)

/** Product details resolved from a barcode (Open Food Facts). */
data class ProductInfo(
    val barcode: String,
    val name: String,
    val brand: String?,
    val category: FoodCategory?,
)

/** Output of reading a label: raw text, barcode, and the expiry-date candidates found. */
data class ScanResult(
    val lines: List<String> = emptyList(),
    val barcode: String? = null,
    val candidates: List<DateCandidate> = emptyList(),
    val suggestedName: String? = null,
) {
    val bestCandidate: DateCandidate? get() = candidates.firstOrNull()
    val isEmpty: Boolean get() = lines.isEmpty() && barcode == null
}
