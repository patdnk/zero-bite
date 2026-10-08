package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/** Turns text and a barcode already read from a label into a ScanResult. */
object LabelScanner {
    fun analyse(lines: List<String>, barcode: String?, today: LocalDate): ScanResult = ScanResult(
        lines = lines,
        barcode = barcode,
        candidates = ExpiryDateParser(today).parse(lines),
        suggestedName = ProductNameGuesser.guess(lines),
    )
}
