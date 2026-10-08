package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.core.model.ItemDraft
import com.mazesys.zerobite.core.model.StorageLocation
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppTextField
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.LabeledInput
import com.mazesys.zerobite.ui.components.Overline
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun ItemFormScreen(viewModel: ItemFormViewModel, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors
    var pickingDate by remember { mutableStateOf(false) }
    val draft = ui.draft

    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Close(onCancel))
        if (!ui.isLoaded) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DetailTitle(title = ui.title, overline = ui.overline)
            LabeledInput(
                label = "Name",
                value = draft.name,
                onValueChange = { name -> viewModel.updateDraft { it.copy(name = name) } },
                placeholder = "Greek yogurt",
                testTag = "itemForm.name",
                autoFocus = draft.name.isEmpty(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            LabeledInput(
                label = "Brand (optional, helps spot recalls)",
                value = draft.brand.orEmpty(),
                onValueChange = { brand -> viewModel.updateDraft { it.copy(brand = brand.takeIf { b -> b.isNotBlank() }) } },
                placeholder = "Yeo Valley",
                testTag = "itemForm.brand",
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Category")
                ChipFilter(FoodCategory.entries, draft.category, { c -> viewModel.updateDraft { it.copy(category = c) } }, { it.label }, Modifier.bleed(Spacing.gutter))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Stored in")
                ChipFilter(StorageLocation.entries, draft.storage, { s -> viewModel.updateDraft { it.copy(storage = s) } }, { it.label }, Modifier.bleed(Spacing.gutter))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline("Quantity")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AppTextField(
                        ui.quantityText, viewModel::setQuantityText, "Amount",
                        Modifier.weight(1f).testTag("itemForm.quantity"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    AppTextField(
                        draft.unit, { unit -> viewModel.updateDraft { it.copy(unit = unit) } }, "Unit",
                        Modifier.width(110.dp).testTag("itemForm.unit"),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                }
                ChipFilter(ItemDraft.unitSuggestions, draft.unit, { u -> viewModel.updateDraft { it.copy(unit = u) } }, { it }, Modifier.bleed(Spacing.gutter))
            }
            CardSection(
                title = "Expiry",
                footer = "No date on the label? Use the typical shelf life for ${draft.category.label.lowercase()}.",
                rows = listOf<@Composable () -> Unit>(
                    {
                        InfoRow("Expires on", icon = Icons.Rounded.Event) {
                            Text(
                                DateText.abbreviated(draft.expiresOn),
                                style = ZeroBiteTheme.type.subheadline.medium(),
                                color = colors.textPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.control))
                                    .background(colors.chipFill)
                                    .clickable { pickingDate = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    },
                    {
                        ActionRow(
                            "Use typical shelf life (${ui.defaultShelfLifeDays} days)",
                            onClick = viewModel::applyDefaultShelfLife,
                            icon = Icons.Rounded.AutoAwesome,
                            tint = colors.brandOrange,
                        )
                    },
                ),
            )
            LabeledInput(
                label = "Notes",
                value = draft.notes,
                onValueChange = { notes -> viewModel.updateDraft { it.copy(notes = notes) } },
                placeholder = "Opened on Monday, half left",
                singleLine = false,
            )
            draft.barcode?.let { code -> CardSection(rows = listOf<@Composable () -> Unit>({ InfoRow("Barcode", code, icon = Icons.Rounded.QrCode) })) }
            ui.errorMessage?.let { Text(it, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.statusExpired) }
        }
        AppButton(
            text = ui.saveTitle,
            onClick = viewModel::save,
            enabled = ui.canSave,
            loading = ui.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.backgroundPrimary)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                .testTag("itemForm.save"),
        )
    }

    if (pickingDate) {
        ExpiryDatePicker(
            initial = draft.expiresOn,
            onPicked = { date -> pickingDate = false; viewModel.updateDraft { it.copy(expiresOn = date) } },
            onDismiss = { pickingDate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePicker(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    // The Material picker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.backgroundPrimary,
        selectedDayContainerColor = colors.textPrimary,
        selectedDayContentColor = colors.onInk,
        todayDateBorderColor = colors.brandOrange,
        todayContentColor = colors.brandOrange,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                } ?: onDismiss()
            }) { Text("Done", color = colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = colors.textSecondary) } },
        colors = pickerColors,
    ) {
        DatePicker(state, colors = pickerColors, showModeToggle = false)
    }
}
