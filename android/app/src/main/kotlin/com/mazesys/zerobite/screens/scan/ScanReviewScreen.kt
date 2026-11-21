package com.mazesys.zerobite.screens.scan

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.DateCandidate
import com.mazesys.zerobite.core.model.FoodCategory
import com.mazesys.zerobite.ui.components.ActionRow
import com.mazesys.zerobite.ui.components.AppButton
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
import com.mazesys.zerobite.ui.format.appLocale
import com.mazesys.zerobite.ui.text.labelRes
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** Check what was read: name, category, product and which date is the expiry. */
@Composable
fun ScanReviewScreen(viewModel: ScanReviewViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors

    Column(Modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            DetailTitle(title = stringResource(R.string.scan_review_title), overline = stringResource(R.string.scan_review_overline))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LabeledInput(
                    label = stringResource(R.string.form_name),
                    value = ui.name,
                    onValueChange = viewModel::setName,
                    placeholder = stringResource(R.string.scan_name_placeholder),
                    testTag = "review.name",
                )
                if (ui.isLookingUp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(color = colors.textSecondary, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.scan_looking_up), style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Overline(stringResource(R.string.form_category))
                ChipFilter(
                    FoodCategory.entries, ui.category, viewModel::setCategory,
                    label = { stringResource(it.labelRes) },
                    modifier = Modifier.bleed(Spacing.gutter),
                )
            }

            if (ui.product != null || ui.barcode != null) {
                CardSection(
                    title = stringResource(R.string.scan_product),
                    rows = buildList<@Composable () -> Unit> {
                        ui.product?.let { product ->
                            add {
                                InfoRow(
                                    stringResource(R.string.scan_product_found),
                                    listOfNotNull(product.brand, product.name).joinToString(" · "),
                                    icon = Icons.Rounded.Verified,
                                )
                            }
                        }
                        ui.barcode?.let { code -> add { InfoRow(stringResource(R.string.detail_barcode), code, icon = Icons.Rounded.QrCode2) } }
                    },
                )
            }

            if (ui.candidates.isEmpty()) {
                CardSection(
                    title = stringResource(R.string.scan_expiry_date),
                    rows = listOf<@Composable () -> Unit>({
                        Text(stringResource(R.string.scan_no_date), style = ZeroBiteTheme.type.subheadline, color = colors.textSecondary)
                    }),
                )
            } else {
                CardSection(
                    title = stringResource(R.string.scan_expiry_date),
                    footer = stringResource(R.string.scan_pick_date),
                    rows = buildList<@Composable () -> Unit> {
                        ui.candidates.forEachIndexed { index, candidate ->
                            add { CandidateRow(candidate, index == ui.selectedIndex) { viewModel.select(index) } }
                        }
                        add { ActionRow(stringResource(R.string.scan_none_of_these), viewModel::clearSelection, icon = Icons.Rounded.Close, tint = colors.textSecondary) }
                    },
                )
            }

            if (ui.lines.isNotEmpty()) {
                CardSection(
                    title = stringResource(R.string.scan_text_read),
                    rows = buildList<@Composable () -> Unit> {
                        add {
                            val count = ui.lines.size
                            ActionRow(
                                if (ui.showsLines) pluralStringResource(R.plurals.scan_lines_hide, count, count)
                                else pluralStringResource(R.plurals.scan_lines_show, count, count),
                                viewModel::toggleLines,
                                icon = if (ui.showsLines) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                tint = colors.textSecondary,
                            )
                        }
                        if (ui.showsLines) add {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                ui.lines.forEach { line ->
                                    Text(line, style = ZeroBiteTheme.type.footnote.copy(fontFamily = FontFamily.Monospace), color = colors.textSecondary)
                                }
                            }
                        }
                    },
                )
            }
        }
        AppButton(
            stringResource(R.string.action_continue),
            onClick = viewModel::continueToForm,
            enabled = ui.canContinue,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("review.continue"),
        )
    }
}

@Composable
private fun CandidateRow(candidate: DateCandidate, isSelected: Boolean, onClick: () -> Unit) {
    val colors = ZeroBiteTheme.colors
    Row(
        Modifier.clickable(role = Role.Checkbox, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(DateText.long(candidate.date, appLocale()), style = ZeroBiteTheme.type.subheadline.semibold(), color = colors.textPrimary)
            Text(
                stringResource(R.string.scan_source_line, candidate.sourceLine),
                style = ZeroBiteTheme.type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        ConfidenceBars(candidate.confidence)
        Icon(
            if (isSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (isSelected) colors.brandOrange else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ConfidenceBars(confidence: Double) {
    val colors = ZeroBiteTheme.colors
    val spoken = stringResource(R.string.scan_confidence, (confidence * 100).toInt())
    Row(Modifier.semantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            Box(
                Modifier.size(width = 4.dp, height = 10.dp)
                    .background(if (index / 3.0 < confidence) colors.brandOrange else colors.chipFill),
            )
        }
    }
}
