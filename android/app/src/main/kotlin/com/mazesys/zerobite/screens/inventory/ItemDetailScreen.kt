package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.mazesys.zerobite.ui.components.RecipeRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.format.appLocale
import com.mazesys.zerobite.ui.text.expiryText
import com.mazesys.zerobite.ui.text.labelRes
import com.mazesys.zerobite.ui.text.putBackRes
import com.mazesys.zerobite.ui.text.quantityAndStorage
import com.mazesys.zerobite.ui.text.quantityLabel
import com.mazesys.zerobite.ui.text.titleRes
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.AppCard
import com.mazesys.zerobite.ui.components.AppDropdownMenu
import com.mazesys.zerobite.ui.components.AppMenuItem
import com.mazesys.zerobite.ui.components.ButtonKind
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.HeaderIconButton
import com.mazesys.zerobite.ui.components.IconTile
import com.mazesys.zerobite.ui.components.InfoRow
import com.mazesys.zerobite.ui.components.ShelfLifeBar
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.format.DateText
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.icon
import com.mazesys.zerobite.ui.theme.semibold
import com.mazesys.zerobite.ui.theme.tint

@Composable
fun ItemDetailScreen(viewModel: ItemDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val colors = ZeroBiteTheme.colors

    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Back(onBack)) {
            if (ui.state == DetailState.ACTIVE || ui.state == DetailState.USED) {
                Box {
                    HeaderIconButton(Icons.Rounded.MoreHoriz, stringResource(R.string.action_more), onClick = { menuOpen = true })
                    AppDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                        AppMenuItem(stringResource(R.string.action_edit), Icons.Rounded.Edit, onClick = { menuOpen = false; viewModel.edit() })
                        AppMenuItem(stringResource(R.string.action_delete), Icons.Rounded.Delete, onClick = { menuOpen = false; confirmDelete = true }, destructive = true)
                    }
                }
            }
        }
        val item = ui.item
        when {
            ui.state == DetailState.LOADING -> Unit
            item == null -> EmptyStateView(
                title = stringResource(R.string.detail_removed_title),
                message = stringResource(R.string.detail_removed_message),
                icon = Icons.Rounded.Delete,
                modifier = Modifier.padding(Spacing.gutter),
            )
            else -> DetailContent(item, ui, viewModel)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title), style = ZeroBiteTheme.type.headline) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = colors.statusExpired, style = ZeroBiteTheme.type.subheadline.semibold())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.textPrimary, style = ZeroBiteTheme.type.subheadline)
                }
            },
            containerColor = colors.backgroundPrimary,
            shape = RoundedCornerShape(Radius.card),
        )
    }
}

@Composable
private fun DetailContent(item: FoodItem, ui: ItemDetailUiState, viewModel: ItemDetailViewModel) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        DetailTitle(title = item.name, overline = stringResource(item.category.labelRes))
        StatusCard(item, ui)
        CardSection(
            title = stringResource(R.string.detail_details),
            rows = buildList<@Composable () -> Unit> {
                add { InfoRow(stringResource(R.string.detail_stored_in), stringResource(item.storage.labelRes), icon = item.storage.icon) }
                add { InfoRow(stringResource(R.string.detail_quantity), item.quantityLabel(), icon = Icons.Rounded.Scale) }
                add { InfoRow(stringResource(R.string.detail_expires), DateText.long(item.expiresOn, appLocale()), icon = Icons.Rounded.Event) }
                ui.addedOn?.let { added -> add { InfoRow(stringResource(R.string.detail_added), DateText.abbreviated(added, appLocale()), icon = Icons.Rounded.AddCircle) } }
                item.barcode?.takeIf { it.isNotEmpty() }?.let { code -> add { InfoRow(stringResource(R.string.detail_barcode), code, icon = Icons.Rounded.QrCode) } }
            },
        )
        if (item.notes.isNotEmpty()) {
            CardSection(title = stringResource(R.string.detail_notes), rows = listOf<@Composable () -> Unit>({
                Text(item.notes, style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textPrimary)
            }))
        }
        if (ui.state == DetailState.ACTIVE) RecipesUsing(item, ui, viewModel)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (ui.state == DetailState.USED) {
                AppButton(stringResource(item.storage.putBackRes), viewModel::restore, kind = ButtonKind.TONAL, icon = Icons.AutoMirrored.Rounded.Undo)
            } else {
                AppButton(stringResource(R.string.detail_mark_used), viewModel::markUsed, icon = Icons.Rounded.Check)
                AppButton(stringResource(R.string.action_edit), viewModel::edit, kind = ButtonKind.TONAL, icon = Icons.Rounded.Edit)
            }
        }
    }
}

@Composable
private fun StatusCard(item: FoodItem, ui: ItemDetailUiState) {
    val colors = ZeroBiteTheme.colors
    val type = ZeroBiteTheme.type
    val status = ui.status ?: return
    val tint = status.tint(colors)
    val used = ui.state == DetailState.USED
    AppCard(accent = if (!used && status.needsAttention) tint else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(item.category.icon, tint = if (used) colors.textSecondary else tint, size = 44.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (used) {
                    Text(stringResource(R.string.detail_used_up), style = type.headline, color = colors.textPrimary)
                    ui.consumedOn?.let { day ->
                        Text(DateText.abbreviated(day, appLocale()), style = type.caption, color = colors.textSecondary)
                    }
                } else {
                    val description = item.expiryText(ui.today)
                    Text(description, style = type.headline, color = tint)
                    // "Expires today" would just repeat the headline, so show what's left instead.
                    Text(
                        if (status == ExpiryStatus.TODAY) item.quantityAndStorage() else stringResource(status.titleRes),
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (!used) ShelfLifeBar(ui.progress, tint, Modifier.padding(top = Spacing.md))
    }
}

@Composable
private fun RecipesUsing(item: FoodItem, ui: ItemDetailUiState, viewModel: ItemDetailViewModel) {
    val rows: List<@Composable () -> Unit> = if (ui.recipeMatches.isEmpty()) {
        listOf {
            Text(stringResource(R.string.detail_no_recipes), style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textSecondary)
        }
    } else {
        ui.recipeMatches.map { match ->
            { RecipeRow(match, Modifier.clickable(role = Role.Button) { viewModel.showRecipe(match) }) }
        }
    }
    CardSection(title = stringResource(R.string.detail_recipes_using, item.name.lowercase()), rows = rows)
}
