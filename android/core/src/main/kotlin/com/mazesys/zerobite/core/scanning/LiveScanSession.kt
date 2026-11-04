package com.mazesys.zerobite.core.scanning

import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/**
 * What the live camera has read so far: every distinct text line (first-seen order) and the
 * first barcode. Immutable, so a frame callback can fold into it atomically. Mirrors the
 * accumulation in iOS LiveScannerView.
 */
data class LiveScanSession(
    val lines: List<String> = emptyList(),
    val barcode: String? = null,
) {
    fun adding(newLines: List<String>, barcodes: List<String>): LiveScanSession {
        val seen = lines.toMutableSet()
        val merged = lines.toMutableList()
        for (raw in newLines) {
            val line = raw.trim()
            if (line.length >= 2 && seen.add(line)) merged += line
        }
        return LiveScanSession(merged, barcode ?: barcodes.firstOrNull())
    }

    fun result(today: LocalDate): ScanResult = LabelScanner.analyse(lines, barcode, today)
}
