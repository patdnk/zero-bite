package com.mazesys.zerobite.screens.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazesys.zerobite.R
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.scanning.PhotoScanner
import com.mazesys.zerobite.scanning.UnreadablePhoto
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ScanPhase {
    data object Idle : ScanPhase
    data object Processing : ScanPhase
    data class Failed(val message: UiText) : ScanPhase
}

/** State machine for the add-food sheet: idle → processing → (review | failed). */
class ScanFlowViewModel(
    val coordinator: ScanCoordinator,
    val supportsLiveScanning: Boolean,
    private val scanner: PhotoScanner,
    private val clock: AppClock,
) : ViewModel() {

    private val _phase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val phase: StateFlow<ScanPhase> = _phase.asStateFlow()

    fun startLiveScan() = coordinator.showLiveScanner()

    fun enterManually() = coordinator.showForm(ItemDraft.new(clock.today()))

    fun liveScanFinished(result: ScanResult) = coordinator.showReview(result)

    /** Runs OCR + barcode detection on a chosen photo and moves to the review screen. */
    fun process(photo: Uri) {
        if (_phase.value == ScanPhase.Processing) return
        _phase.value = ScanPhase.Processing
        viewModelScope.launch {
            _phase.value = try {
                val result = scanner.scan(photo, clock.today())
                if (result.isEmpty) {
                    ScanPhase.Failed(uiText(R.string.scan_error_nothing_found))
                } else {
                    coordinator.showReview(result)
                    ScanPhase.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnreadablePhoto) {
                ScanPhase.Failed(uiText(R.string.scan_error_unreadable))
            } catch (e: Exception) {
                // Whatever went wrong (I/O, recogniser), the user's next step is the same; the raw text is never shown.
                ScanPhase.Failed(uiText(R.string.scan_error_load_failed))
            }
        }
    }

    fun dismissError() {
        _phase.value = ScanPhase.Idle
    }

    fun cancel() = coordinator.finish()
}
