package com.mazesys.zerobite.scanning

import android.net.Uri
import androidx.camera.core.ImageAnalysis
import com.mazesys.zerobite.core.model.ScanResult
import kotlinx.datetime.LocalDate

/** Text lines (top to bottom) and barcode payloads read from one image or camera frame. */
data class RecognizedText(val lines: List<String>, val barcodes: List<String>)

/** One recognised line and the vertical centre of its box, if the recogniser gave one. */
data class PositionedLine(val text: String, val centerY: Int?)

object TextLines {
    /** Top-to-bottom reading order, as the iOS Vision path sorts by box midpoint. */
    fun ordered(lines: List<PositionedLine>): List<String> =
        lines.withIndex()
            .sortedWith(compareBy<IndexedValue<PositionedLine>>({ it.value.centerY == null }, { it.value.centerY ?: 0 }, { it.index }))
            .map { it.value.text.trim() }
            .filter { it.isNotEmpty() }
}

/** The chosen file couldn't be decoded as an image. Carries no text: view models map it to user-facing copy. */
class UnreadablePhoto : Exception()

/** Reads a still photo of a label into a ScanResult (text + barcode + parsed dates). */
fun interface PhotoScanner {
    suspend fun scan(photo: Uri, today: LocalDate): ScanResult
}

/** Label reading for both the photo path and the live camera. */
interface LabelReading : PhotoScanner {
    /** False on devices without a camera; the scan sheet then hides the live option. */
    val supportsLiveScanning: Boolean

    /** A CameraX analyzer that reports what each frame contains. */
    fun analyzer(onFrame: (RecognizedText) -> Unit): ImageAnalysis.Analyzer
}
