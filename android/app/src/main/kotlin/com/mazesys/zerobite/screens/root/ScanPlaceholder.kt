package com.mazesys.zerobite.screens.root

import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme

/** Placeholder for the scan flow until Plan 3 adds CameraX + ML Kit. */
@Composable
fun ScanPlaceholder(onClose: () -> Unit, onAddManually: () -> Unit) {
    Column {
        TopBar(TopBarLeading.Close(onClose))
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            DetailTitle(title = stringResource(R.string.action_scan_label), overline = stringResource(R.string.scan_coming_soon))
            Text(
                stringResource(R.string.scan_coming_soon_message),
                style = ZeroBiteTheme.type.body,
                color = ZeroBiteTheme.colors.textSecondary,
            )
            AppButton(stringResource(R.string.action_add_manually), onAddManually, kind = ButtonKind.TONAL, icon = Icons.Rounded.EditNote)
        }
    }
}
