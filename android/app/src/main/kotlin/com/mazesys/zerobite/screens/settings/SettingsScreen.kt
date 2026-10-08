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
import com.mazesys.zerobite.core.model.Allergen
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
        ScreenHeader(title = "Settings", overline = "Zero Bite ${ui.appVersion}")

        CardSection(
            title = "Reminders",
            footer = "You'll get a heads-up before the date, one on the day, and one if it slips past.",
            rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchRow(
                        "Expiry reminders", ui.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it, requester) },
                        enabled = !ui.isUpdatingNotifications, testTag = "settings.notificationsToggle",
                    )
                }
                if (ui.reminderState == ReminderState.DENIED) {
                    add {
                        ActionRow("Allow in system settings", onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }, icon = Icons.Rounded.Settings, tint = colors.brandOrange)
                    }
                }
                add { AppStepper("Remind me ${s.leadDays} days before", s.leadDays, 1..14, viewModel::setLeadDays) }
                add {
                    InfoRow("At", icon = Icons.Rounded.Schedule) {
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
                    ActionRow("Send a test notification", viewModel::sendTest, icon = Icons.Rounded.Notifications,
                        tint = colors.brandOrange, enabled = ui.notificationsEnabled)
                }
            },
        )

        CardSection(
            title = "Food safety",
            footer = "Checks the Food Standards Agency for UK recalls and tells you if something in your fridge might be affected.",
            rows = listOf<@Composable () -> Unit>(
                { SwitchRow("Check UK food recalls", s.recallChecksEnabled, viewModel::setRecallChecksEnabled, testTag = "settings.recallsToggle") },
                { InfoRow("Food alerts", "Arrives in the next build", icon = Icons.Rounded.Warning) },
            ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Overline("Your allergies")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Allergen.entries.forEach { allergen ->
                    Chip(allergen.label, allergen.raw in s.allergens, onClick = { viewModel.toggleAllergen(allergen) })
                }
            }
            Text("Allergy alerts for these show under Food alerts → For you.", style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
        }

        CardSection(
            title = "Fridge",
            footer = "Recipes that use food inside this window are ranked higher.",
            rows = listOf<@Composable () -> Unit>({
                AppStepper("“Expiring soon” means within ${s.soonThresholdDays} days", s.soonThresholdDays, 1..14, viewModel::setSoonThresholdDays)
            }),
        )

        CardSection(
            title = "Data",
            rows = listOf<@Composable () -> Unit>(
                { InfoRow("In your fridge", "${ui.activeCount}", icon = Icons.Rounded.Kitchen) },
                { InfoRow("Used up", "${ui.usedCount}", icon = Icons.Rounded.Check) },
                { ActionRow("Add sample food", viewModel::loadSampleData, icon = Icons.Rounded.AutoAwesome) },
                { ActionRow("Clear used-up history", viewModel::clearUsedHistory, icon = Icons.Rounded.History, enabled = ui.usedCount > 0) },
                {
                    ActionRow("Delete everything", viewModel::deleteEverything, icon = Icons.Rounded.Delete, tint = colors.statusExpired,
                        enabled = ui.activeCount + ui.usedCount > 0)
                },
            ),
        )

        CardSection(
            title = "About",
            footer = "Dates and barcodes are read on your device. Product names come from Open Food Facts. " +
                "Food alerts come from the Food Standards Agency under the Open Government Licence v3.0.",
            rows = listOf<@Composable () -> Unit>(
                { ActionRow("How Zero Bite works", viewModel::showHowItWorks, icon = Icons.AutoMirrored.Rounded.MenuBook, tint = colors.brandOrange) },
                { InfoRow("Version", ui.appVersion) },
                { InfoRow("Recipes", "${ui.recipeCount}") },
            ),
        )
    }

    if (pickingTime) ReminderTimeDialog(s.notifyHour, s.notifyMinute, onDismiss = { pickingTime = false }) { h, m ->
        pickingTime = false
        viewModel.setNotifyTime(h, m)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onPicked: (Int, Int) -> Unit) {
    val colors = ZeroBiteTheme.colors
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text("Done", color = colors.textPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } },
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
