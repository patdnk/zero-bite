package com.mazesys.zerobite.screens.scan

import androidx.lifecycle.ViewModel
import com.mazesys.zerobite.core.model.ScanResult
import com.mazesys.zerobite.core.scanning.LiveScanSession
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.scanning.RecognizedText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** What the camera has read so far. The screen formats the date and the line count in the app language. */
data class LiveScanUiState(
    val session: LiveScanSession = LiveScanSession(),
    val result: ScanResult = ScanResult(),
) {
    val bestDate: LocalDate? get() = result.bestCandidate?.date
    val lineCount: Int get() = session.lines.size
    val canFinish: Boolean get() = !result.isEmpty
}

/** Folds camera frames into what's been read so far (iOS LiveScannerView's state). */
class LiveScanViewModel(
    private val clock: AppClock,
    private val onFinish: (ScanResult) -> Unit,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveScanUiState())
    val state: StateFlow<LiveScanUiState> = _state.asStateFlow()

    fun onFrame(text: RecognizedText) = _state.update { current ->
        val session = current.session.adding(text.lines, text.barcodes)
        // Most frames add nothing new; skip the re-parse then.
        if (session == current.session) current else LiveScanUiState(session, session.result(clock.today()))
    }

    fun finish() {
        val result = _state.value.result
        if (!result.isEmpty) onFinish(result)
    }
}
