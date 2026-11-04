package com.mazesys.zerobite.screens.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.R
import com.mazesys.zerobite.coordination.AlertButton
import com.mazesys.zerobite.coordination.AlertConfig
import com.mazesys.zerobite.scanning.LabelReading
import com.mazesys.zerobite.screens.root.AppAlert
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.format.appLocale
import com.mazesys.zerobite.ui.text.UiText
import com.mazesys.zerobite.ui.text.uiText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

enum class CameraAccess {
    ASKING, GRANTED, DENIED;

    /**
     * The access after the screen resumes (the system prompt and the Settings app both pause it).
     * A grant is picked up at once; without one, ASKING stays ASKING (the prompt may still be up)
     * and DENIED stays DENIED.
     */
    fun afterResume(granted: Boolean): CameraAccess = if (granted) GRANTED else this
}

/** Live label scanning: asks for the camera, previews it and reads every frame. */
@Composable
fun LiveScannerScreen(viewModel: LiveScanViewModel, labels: LabelReading, onBack: () -> Unit, onUsePhoto: () -> Unit) {
    val context = LocalContext.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var access by rememberSaveable { mutableStateOf(if (granted()) CameraAccess.GRANTED else CameraAccess.ASKING) }
    var cameraError by remember { mutableStateOf<UiText?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        access = if (ok) CameraAccess.GRANTED else CameraAccess.DENIED
    }
    LaunchedEffect(Unit) {
        if (access == CameraAccess.ASKING) request.launch(Manifest.permission.CAMERA)
    }
    // After "Open settings" the user may grant the camera and come back: start without reopening the sheet.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = access.afterResume(granted()) }
    val analyzer = remember(labels) { labels.analyzer(viewModel::onFrame) }

    LiveScannerContent(
        access = access,
        ui = ui,
        cameraError = cameraError,
        onFinish = viewModel::finish,
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        onUsePhoto = onUsePhoto,
        onBack = onBack,
        onDismissError = { cameraError = null },
    ) {
        CameraPreview(analyzer, onError = { cameraError = it })
    }
}

/** Stateless body, so the permission states and the summary card are testable without a camera. */
@Composable
fun LiveScannerContent(
    access: CameraAccess,
    ui: LiveScanUiState,
    cameraError: UiText?,
    onFinish: () -> Unit,
    onOpenSettings: () -> Unit,
    onUsePhoto: () -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (access) {
                CameraAccess.GRANTED -> {
                    preview()
                    SummaryCard(ui, onFinish, Modifier.align(Alignment.BottomCenter))
                }
                CameraAccess.DENIED -> EmptyStateView(
                    title = stringResource(R.string.scan_camera_off_title),
                    message = stringResource(R.string.scan_camera_rationale),
                    icon = Icons.Rounded.PhotoCamera,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Spacing.gutter),
                    primaryTitle = stringResource(R.string.scan_open_settings),
                    onPrimary = onOpenSettings,
                    secondaryTitle = stringResource(R.string.scan_choose_photo_instead),
                    onSecondary = onUsePhoto,
                )
                CameraAccess.ASKING -> Unit
            }
        }
    }
    if (cameraError != null) {
        AppAlert(
            AlertConfig(title = uiText(R.string.scan_camera_unavailable_title), message = cameraError, primary = AlertButton(uiText(R.string.action_ok))),
            onDismiss = onDismissError,
        )
    }
}

@Composable
private fun SummaryCard(ui: LiveScanUiState, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    Column(
        modifier.padding(Spacing.lg).fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.94f), RoundedCornerShape(Radius.card))
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val date = ui.bestDate
        if (date != null) {
            Found(Icons.Rounded.CalendarMonth, DateText.long(date, appLocale()), emphasised = true)
        } else {
            Found(Icons.Rounded.CalendarMonth, stringResource(R.string.scan_live_looking_for_date), muted = true)
        }
        ui.result.barcode?.let { Found(Icons.Rounded.QrCode2, it) }
        ui.result.suggestedName?.let { Found(Icons.Rounded.LocalOffer, it) }
        Text(
            pluralStringResource(R.plurals.scan_live_lines_read, ui.lineCount, ui.lineCount),
            style = ZeroBiteTheme.type.caption,
            color = colors.textSecondary,
        )
        AppButton(stringResource(R.string.scan_live_use_found), onFinish, enabled = ui.canFinish)
    }
}

@Composable
private fun Found(icon: ImageVector, text: String, emphasised: Boolean = false, muted: Boolean = false) {
    val colors = ZeroBiteTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Icon(icon, contentDescription = null, tint = if (muted) colors.textSecondary else colors.textPrimary, modifier = Modifier.size(18.dp))
        Text(
            text,
            style = if (emphasised) ZeroBiteTheme.type.headline.semibold() else ZeroBiteTheme.type.subheadline,
            color = if (muted) colors.textSecondary else colors.textPrimary,
        )
    }
}

/** CameraX preview + analysis bound to this screen's lifecycle; unbound when it leaves. */
@Composable
private fun CameraPreview(analyzer: ImageAnalysis.Analyzer, onError: (UiText) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val useCases = mutableListOf<UseCase>()
        future.addListener({
            // The provider arrives asynchronously: if the screen already left, binding now would keep the camera open.
            if (disposed) return@addListener
            try {
                val cameras = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                useCases += listOf(preview, analysis)
                cameras.unbindAll()
                cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // The reason (in use, disabled by policy...) isn't actionable beyond "try again": no raw exception text.
                onError(uiText(R.string.scan_camera_unavailable_message))
            }
        }, executor)
        onDispose {
            disposed = true
            provider?.unbind(*useCases.toTypedArray())
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}
