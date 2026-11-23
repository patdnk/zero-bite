package com.mazesys.zerobite.screens.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.R
import com.mazesys.zerobite.coordination.ScanCoordinator
import com.mazesys.zerobite.coordination.ScanCoordinator.Event
import com.mazesys.zerobite.coordination.ScanCoordinator.Failure
import com.mazesys.zerobite.coordination.ScanCoordinator.Status
import com.mazesys.zerobite.core.time.AppClock
import com.mazesys.zerobite.screens.root.AlertButton
import com.mazesys.zerobite.screens.root.AlertConfig
import com.mazesys.zerobite.screens.root.AppAlert
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.LoadingOverlay
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.text.uiText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

/** First screen of the add-food sheet: camera, photo or typing. */
@Composable
fun ScanFlowScreen(coordinator: ScanCoordinator, clock: AppClock) {
    val status = coordinator.state.status
    val colors = ZeroBiteTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) coordinator.eventLoop(Event.DidChoosePhoto(uri, clock.today()))
    }

    Box(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(TopBarLeading.Close { coordinator.eventLoop(Event.DidTapClose) })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                DetailTitle(title = stringResource(R.string.fridge_add_food), overline = stringResource(R.string.scan_overline))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (coordinator.supportsLiveScanning) {
                        ScanOptionRow(
                            Icons.Rounded.QrCodeScanner, stringResource(R.string.scan_option_camera), stringResource(R.string.scan_option_camera_detail),
                            isPrimary = true, onClick = { coordinator.eventLoop(Event.DidTapLiveScan) }, modifier = Modifier.testTag("scan.live"),
                        )
                    }
                    ScanOptionRow(
                        Icons.Rounded.PhotoLibrary, stringResource(R.string.scan_option_photo), stringResource(R.string.scan_option_photo_detail),
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.testTag("scan.photo"),
                    )
                    ScanOptionRow(
                        Icons.Rounded.EditNote, stringResource(R.string.scan_option_type), stringResource(R.string.scan_option_type_detail),
                        onClick = { coordinator.eventLoop(Event.DidTapManualEntry(clock.today())) }, modifier = Modifier.testTag("scan.manual"),
                    )
                }
                Text(stringResource(R.string.scan_hint), style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
            }
        }
        LoadingOverlay(status == Status.Processing, stringResource(R.string.scan_reading))
    }

    (status as? Status.Failed)?.let { failed ->
        AppAlert(
            AlertConfig(title = uiText(R.string.scan_error_title), message = uiText(failed.failure.messageRes), primary = AlertButton(uiText(R.string.action_ok))),
            onDismiss = { coordinator.eventLoop(Event.DidDismissError) },
        )
    }
}

private val Failure.messageRes: Int
    get() = when (this) {
        Failure.NOTHING_FOUND -> R.string.scan_error_nothing_found
        Failure.UNREADABLE -> R.string.scan_error_unreadable
        Failure.LOAD_FAILED -> R.string.scan_error_load_failed
    }

@Composable
private fun ScanOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
) {
    val colors = ZeroBiteTheme.colors
    AppCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                Modifier.size(44.dp).background(
                    if (isPrimary) colors.brandOrange else colors.brandOrange.copy(alpha = 0.12f),
                    RoundedCornerShape(Radius.control),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (isPrimary) Color.White else colors.brandOrange, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary)
                Text(subtitle, style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(12.dp))
        }
    }
}
