package com.mazesys.zerobite.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.core.model.ExpiryStatus
import com.mazesys.zerobite.core.model.FoodItem
import com.mazesys.zerobite.ui.components.AppDropdownMenu
import com.mazesys.zerobite.ui.components.AppMenuItem
import com.mazesys.zerobite.ui.components.BottomSearchBar
import com.mazesys.zerobite.ui.components.ChipFilter
import com.mazesys.zerobite.ui.components.EmptyStateView
import com.mazesys.zerobite.ui.components.FoodItemCard
import com.mazesys.zerobite.ui.components.HeaderIconButton
import com.mazesys.zerobite.ui.components.ScreenHeader
import com.mazesys.zerobite.ui.components.SearchAccessory
import com.mazesys.zerobite.ui.components.SectionHeading
import com.mazesys.zerobite.ui.components.UseFirstCard
import com.mazesys.zerobite.ui.components.bleed
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.tint

@Composable
fun InventoryScreen(viewModel: InventoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).testTag("inventory.grid"),
            contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            fullWidth("header") {
                ScreenHeader(
                    title = "Fridge",
                    overline = state.headerDate,
                    count = if (state.isLoading || state.isEmpty) null else state.activeItems.size,
                ) {
                    AddMenu(onScan = viewModel::showScan, onAddManually = viewModel::showAddItem)
                }
            }
            when {
                state.isLoading -> Unit
                state.isEmpty -> fullWidth("empty") {
                    EmptyStateView(
                        title = "Your fridge is empty",
                        message = "Scan a label or add something by hand. We'll remind you before it goes off and suggest what to cook.",
                        icon = Icons.Rounded.Kitchen,
                        modifier = Modifier.padding(top = 40.dp),
                        primaryTitle = "Scan a label",
                        primaryIcon = Icons.Rounded.QrCodeScanner,
                        onPrimary = viewModel::showScan,
                        secondaryTitle = "Try it with sample food",
                        onSecondary = viewModel::loadSampleData,
                    )
                }
                else -> fridgeContent(state, viewModel)
            }
        }
        if (!state.isLoading && !state.isEmpty) {
            BottomSearchBar(
                text = state.searchText,
                onTextChange = viewModel::setSearchText,
                placeholder = "Search your food",
                accessory = SearchAccessory(Icons.Rounded.QrCodeScanner, "Scan label or barcode", viewModel::showScan),
                testTag = "inventory.search",
            )
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.fridgeContent(state: InventoryUiState, viewModel: InventoryViewModel) {
    state.useFirstItem?.let { hero ->
        fullWidth("useFirst") {
            UseFirstCard(
                item = hero,
                today = state.today,
                progress = state.useFirstProgress,
                soonThresholdDays = state.soonThresholdDays,
                othersNeedingAttention = state.othersNeedingAttention,
                onOpen = { viewModel.showDetails(hero) },
                onUsed = { viewModel.markUsed(hero) },
                onFreeze = if (state.canFreeze(hero)) ({ viewModel.freeze(hero) }) else null,
                // Recipes for an item live on its detail screen (Plan 3 adds them there).
                onRecipes = { viewModel.showDetails(hero) },
                modifier = Modifier.testTag("inventory.useFirst").padding(top = Spacing.sm),
            )
        }
    }
    fullWidth("filter") {
        ChipFilter(
            options = StorageFilter.entries,
            selected = state.storageFilter,
            onSelect = viewModel::setStorageFilter,
            label = { it.title },
            modifier = Modifier.bleed(Spacing.gutter).padding(vertical = Spacing.xs),
        )
    }
    if (state.gridSections.isEmpty()) {
        fullWidth("nothing") {
            Text(
                state.emptyGridMessage,
                style = ZeroBiteTheme.type.subheadline,
                color = ZeroBiteTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
            )
        }
    }
    state.gridSections.forEach { section ->
        fullWidth("heading-${section.status}") {
            val count = section.items.size
            SectionHeading(
                title = section.status.sectionTitle,
                detail = if (count == 1) "1 item" else "$count items",
                tint = if (section.status == ExpiryStatus.FRESH) ZeroBiteTheme.colors.textPrimary else section.status.tint(ZeroBiteTheme.colors),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        items(section.items, key = { it.id }) { item -> CardWithMenu(item, state, viewModel) }
    }
}

@Composable
private fun CardWithMenu(item: FoodItem, state: InventoryUiState, viewModel: InventoryViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        FoodItemCard(
            item = item,
            today = state.today,
            soonThresholdDays = state.soonThresholdDays,
            onClick = { viewModel.showDetails(item) },
            onLongClick = { menuOpen = true },
        )
        AppDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            AppMenuItem("Used it", Icons.Rounded.Check, onClick = { menuOpen = false; viewModel.markUsed(item) })
            if (state.canFreeze(item)) {
                AppMenuItem("Move to freezer", Icons.Rounded.AcUnit, onClick = { menuOpen = false; viewModel.freeze(item) })
            }
            AppMenuItem("Edit", Icons.Rounded.Edit, onClick = { menuOpen = false; viewModel.showEditItem(item) })
            AppMenuItem("Delete", Icons.Rounded.Delete, onClick = { menuOpen = false; viewModel.delete(item) }, destructive = true)
        }
    }
}

@Composable
private fun AddMenu(onScan: () -> Unit, onAddManually: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(Icons.Rounded.Add, "Add food", onClick = { open = true }, modifier = Modifier.testTag("inventory.addButton"))
        AppDropdownMenu(expanded = open, onDismiss = { open = false }) {
            AppMenuItem("Scan label or barcode", Icons.Rounded.QrCodeScanner, onClick = { open = false; onScan() })
            AppMenuItem("Add manually", Icons.Rounded.EditNote, onClick = { open = false; onAddManually() })
        }
    }
}
