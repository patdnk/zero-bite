package com.mazesys.zerobite.scanning

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LabelScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.coroutines.resume

/**
 * On-device OCR and barcode reading with ML Kit's bundled models (no download, works offline).
 * Clients are created on first use: building AppDependencies must not start ML Kit, because the
 * Robolectric launch tests build the live dependencies.
 */
class MlKitLabelReader(private val context: Context) : LabelReading {

    private val text: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val barcodes: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                // iOS reads EAN-13 (which covers UPC-A), EAN-8, UPC-E, Code 128 and QR.
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
                )
                .build(),
        )
    }

    override val supportsLiveScanning: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

    /** Reads text and barcodes; a failed barcode pass just means no barcode. Null if OCR failed. */
    private fun read(image: InputImage, onResult: (RecognizedText?) -> Unit) {
        val textTask = text.process(image)
        val codeTask = barcodes.process(image)
        Tasks.whenAllComplete(textTask, codeTask).addOnCompleteListener {
            if (!textTask.isSuccessful) {
                onResult(null)
                return@addOnCompleteListener
            }
            val lines = textTask.result.textBlocks.flatMap { block ->
                block.lines.map { PositionedLine(it.text, it.boundingBox?.centerY()) }
            }
            val codes = if (codeTask.isSuccessful) codeTask.result.mapNotNull { it.rawValue } else emptyList()
            onResult(RecognizedText(TextLines.ordered(lines), codes))
        }
    }

    override suspend fun scan(photo: Uri, today: LocalDate): ScanResult {
        // Decoding a full-size photo is disk I/O plus a large allocation: keep it off the main thread.
        val image = withContext(Dispatchers.IO) {
            try {
                // A file that isn't an image fails here (ImageDecoder.DecodeException is an IOException).
                InputImage.fromFilePath(context, photo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnreadablePhoto) {
                throw e
            } catch (e: Exception) {
                throw UnreadablePhoto()
            }
        }
        val recognized = suspendCancellableCoroutine<RecognizedText?> { continuation -> read(image) { continuation.resume(it) } }
            ?: throw IOException()
        return LabelScanner.analyse(recognized.lines, recognized.barcodes.firstOrNull(), today)
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyzer(onFrame: (RecognizedText) -> Unit) = ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return@Analyzer
        }
        // The frame stays open until ML Kit is done, so CameraX drops frames instead of queueing them.
        // Whatever fails, the proxy must close or CameraX stops delivering frames.
        try {
            read(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)) { result ->
                try {
                    if (result != null) onFrame(result)
                } finally {
                    proxy.close()
                }
            }
        } catch (e: Exception) {
            proxy.close() // Starting the read failed, so its callback will never run: drop this frame.
        }
    }
}
