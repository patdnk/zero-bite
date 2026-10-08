package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
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
import com.mazesys.zerobite.core.model.FoodItem
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
                    HeaderIconButton(Icons.Rounded.MoreHoriz, "More", onClick = { menuOpen = true })
                    AppDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                        AppMenuItem("Edit", Icons.Rounded.Edit, onClick = { menuOpen = false; viewModel.edit() })
                        AppMenuItem("Delete", Icons.Rounded.Delete, onClick = { menuOpen = false; confirmDelete = true }, destructive = true)
                    }
                }
            }
        }
        val item = ui.item
        when {
            ui.state == DetailState.LOADING -> Unit
            item == null -> EmptyStateView(
                title = "Item removed",
                message = "This item is no longer in your fridge.",
                icon = Icons.Rounded.Delete,
                modifier = Modifier.padding(Spacing.gutter),
            )
            else -> DetailContent(item, ui, viewModel)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this item?", style = ZeroBiteTheme.type.headline) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Delete", color = colors.statusExpired, style = ZeroBiteTheme.type.subheadline.semibold())
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = colors.textPrimary, style = ZeroBiteTheme.type.subheadline)
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
        DetailTitle(title = item.name, overline = item.category.label)
        StatusCard(item, ui)
        CardSection(
            title = "Details",
            rows = buildList<@Composable () -> Unit> {
                add { InfoRow("Stored in", item.storage.label, icon = item.storage.icon) }
                add { InfoRow("Quantity", item.quantityText, icon = Icons.Rounded.Scale) }
                add { InfoRow("Expires", DateText.long(item.expiresOn), icon = Icons.Rounded.Event) }
                ui.addedOn?.let { added -> add { InfoRow("Added", DateText.abbreviated(added), icon = Icons.Rounded.AddCircle) } }
                item.barcode?.takeIf { it.isNotEmpty() }?.let { code -> add { InfoRow("Barcode", code, icon = Icons.Rounded.QrCode) } }
            },
        )
        if (item.notes.isNotEmpty()) {
            CardSection(title = "Notes", rows = listOf<@Composable () -> Unit>({
                Text(item.notes, style = ZeroBiteTheme.type.subheadline, color = ZeroBiteTheme.colors.textPrimary)
            }))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (ui.state == DetailState.USED) {
                AppButton("Put it back in the fridge", viewModel::restore, kind = ButtonKind.TONAL, icon = Icons.AutoMirrored.Rounded.Undo)
            } else {
                AppButton("Mark as used up", viewModel::markUsed, icon = Icons.Rounded.Check)
                AppButton("Edit", viewModel::edit, kind = ButtonKind.TONAL, icon = Icons.Rounded.Edit)
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
                    Text("Used up", style = type.headline, color = colors.textPrimary)
                    ui.consumedOn?.let { day ->
                        Text(DateText.abbreviated(day), style = type.caption, color = colors.textSecondary)
                    }
                } else {
                    val description = item.expiryDescription(ui.today)
                    Text(description, style = type.headline, color = tint)
                    // "Expires today" would just repeat the headline, so show what's left instead.
                    Text(
                        if (description == status.sectionTitle) "${item.quantityText} · ${item.storage.label}" else status.sectionTitle,
                        style = type.caption,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        if (!used) ShelfLifeBar(ui.progress, tint, Modifier.padding(top = Spacing.md))
    }
}
