package com.mazesys.zerobite.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.Allergen
import com.mazesys.zerobite.coordination.SettingsAlert
import com.mazesys.zerobite.screens.root.AlertButton
import com.mazesys.zerobite.screens.root.AlertConfig
import com.mazesys.zerobite.screens.root.AppAlert
import com.mazesys.zerobite.ui.text.uiText
import com.mazesys.zerobite.ui.text.labelRes
import com.mazesys.zerobite.reminders.LocalPermissionRequester
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppStepper
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.Chip
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SwitchRow
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val requester = LocalPermissionRequester.current
    val context = LocalContext.current
    val colors = ZeroBiteTheme.colors
    var pickingTime by remember { mutableStateOf(false) }
    val s = ui.snapshot

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose {}
    }

    Column(
        modifier.fillMaxSize().background(colors.backgroundPrimary).verticalScroll(rememberScrollState())
            .padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), overline = stringResource(R.string.settings_version_overline, ui.appVersion))

        CardSection(
            title = stringResource(R.string.settings_reminders),
            footer = stringResource(R.string.settings_reminders_footer),
            rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchRow(
                        stringResource(R.string.settings_expiry_reminders), ui.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it, requester) },
                        enabled = !ui.isUpdatingNotifications, testTag = "settings.notificationsToggle",
                    )
                }
                if (ui.reminderState == ReminderState.DENIED) {
                    add {
                        ActionRow(stringResource(R.string.settings_allow_in_system), onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }, icon = Icons.Rounded.Settings, tint = colors.brandOrange)
                    }
                }
                add { AppStepper(pluralStringResource(R.plurals.settings_remind_days_before, s.leadDays, s.leadDays), s.leadDays, 1..14, viewModel::setLeadDays) }
                add {
                    InfoRow(stringResource(R.string.settings_at), icon = Icons.Rounded.Schedule) {
                        Text(
                            ui.notifyTimeText,
                            style = ZeroBiteTheme.type.subheadline.medium(),
                            color = colors.textPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.control)).background(colors.chipFill)
                                .clickable { pickingTime = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                add {
                    ActionRow(stringResource(R.string.settings_send_test), viewModel::sendTest, icon = Icons.Rounded.Notifications,
                        tint = colors.brandOrange, enabled = ui.notificationsEnabled)
                }
            },
        )

        CardSection(
            title = stringResource(R.string.settings_food_safety),
            footer = stringResource(R.string.settings_food_safety_footer),
            rows = listOf<@Composable () -> Unit>(
                { SwitchRow(stringResource(R.string.settings_check_recalls), s.recallChecksEnabled, viewModel::setRecallChecksEnabled, testTag = "settings.recallsToggle") },
                { InfoRow(stringResource(R.string.settings_food_alerts), stringResource(R.string.settings_next_build), icon = Icons.Rounded.Warning) },
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Overline(stringResource(R.string.settings_your_allergies))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Allergen.entries.forEach { allergen ->
                    Chip(stringResource(allergen.labelRes), allergen.raw in s.allergens, onClick = { viewModel.toggleAllergen(allergen) })
                }
            }
            Text(stringResource(R.string.settings_allergies_footer), style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }

        CardSection(
            title = stringResource(R.string.settings_fridge),
            footer = stringResource(R.string.settings_fridge_footer),
            rows = listOf<@Composable () -> Unit>({
                AppStepper(pluralStringResource(R.plurals.settings_soon_threshold, s.soonThresholdDays, s.soonThresholdDays), s.soonThresholdDays, 1..14, viewModel::setSoonThresholdDays)
            }),
        )

        CardSection(
            title = stringResource(R.string.settings_privacy),
            footer = stringResource(R.string.settings_privacy_footer),
            rows = listOf<@Composable () -> Unit>({
                SwitchRow(stringResource(R.string.settings_share_usage), s.shareUsageData, viewModel::setShareUsageData, testTag = "settings.shareUsageToggle")
            }),
        )

        CardSection(
            title = stringResource(R.string.settings_data),
            rows = listOf<@Composable () -> Unit>(
                { InfoRow(stringResource(R.string.settings_in_your_fridge), "${ui.activeCount}", icon = Icons.Rounded.Kitchen) },
                { InfoRow(stringResource(R.string.settings_used_up), "${ui.usedCount}", icon = Icons.Rounded.Check) },
                { ActionRow(stringResource(R.string.settings_add_sample), viewModel::loadSampleData, icon = Icons.Rounded.AutoAwesome) },
                { ActionRow(stringResource(R.string.settings_clear_history), viewModel::clearUsedHistory, icon = Icons.Rounded.History, enabled = ui.usedCount > 0) },
                {
                    ActionRow(stringResource(R.string.settings_delete_everything), viewModel::deleteEverything, icon = Icons.Rounded.Delete, tint = colors.statusExpired,
                        enabled = ui.activeCount + ui.usedCount > 0)
                },
            ),
        )

        CardSection(
            title = stringResource(R.string.settings_about),
            footer = stringResource(R.string.settings_about_footer),
            rows = listOf<@Composable () -> Unit>(
                { ActionRow(stringResource(R.string.settings_how_it_works), viewModel::showHowItWorks, icon = Icons.AutoMirrored.Rounded.MenuBook, tint = colors.brandOrange) },
                { InfoRow(stringResource(R.string.settings_version), ui.appVersion) },
                { InfoRow(stringResource(R.string.settings_recipes), "${ui.recipeCount}") },
            ),
        )
    }

    if (pickingTime) ReminderTimeDialog(s.notifyHour, s.notifyMinute, onDismiss = { pickingTime = false }) { h, m ->
        pickingTime = false
        viewModel.setNotifyTime(h, m)
    }
}

/**
 * Settings' alert, drawn by MainScreen over whichever tab is showing: a refused notification
 * permission can come back after the user has already moved to another tab.
 */
@Composable
fun SettingsAlertHost(viewModel: SettingsViewModel) {
    viewModel.alert?.let { alert ->
        SettingsAlertDialog(alert, onConfirm = { viewModel.confirmAlert(alert) }, onDismiss = viewModel::dismissAlert)
    }
}

/** An info message gets OK; a confirmation gets its destructive action and Cancel. */
@Composable
private fun SettingsAlertDialog(alert: SettingsAlert, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val action = alert.actionTitle
    AppAlert(
        AlertConfig(
            title = alert.title,
            message = alert.message,
            primary = if (action == null) AlertButton(uiText(R.string.action_ok), action = onConfirm) else AlertButton(action, isDestructive = true, action = onConfirm),
            secondary = if (action == null) null else AlertButton(uiText(R.string.action_cancel)),
        ),
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPicked: (Int, Int) -> Unit) {
    val colors = ZeroBiteTheme.colors
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text(stringResource(R.string.action_done), color = colors.textPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = colors.textSecondary) } },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.chipFill,
                    selectorColor = colors.brandOrange,
                    timeSelectorSelectedContainerColor = colors.brandOrange.copy(alpha = 0.18f),
                    timeSelectorUnselectedContainerColor = colors.chipFill,
                ),
            )
        },
        containerColor = colors.backgroundPrimary,
        shape = RoundedCornerShape(Radius.card),
    )
}
