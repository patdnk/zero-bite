package com.mazesys.zerobite.screens.recipes

import androidx.compose.ui.res.stringResource
import com.mazesys.zerobite.R
import com.mazesys.zerobite.ui.text.quantityLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazesys.zerobite.ui.components.AppButton
import com.mazesys.zerobite.ui.components.CardSection
import com.mazesys.zerobite.ui.components.DetailTitle
import com.mazesys.zerobite.ui.components.TopBar
import com.mazesys.zerobite.ui.components.TopBarLeading
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium

@Composable
fun CookedScreen(viewModel: CookedViewModel, modifier: Modifier = Modifier) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = ZeroBiteTheme.colors
    Column(modifier.fillMaxSize().background(colors.backgroundPrimary)) {
        TopBar(TopBarLeading.Close(viewModel::cancel))
        if (!ui.isLoaded) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            DetailTitle(title = stringResource(R.string.cooked_title), overline = ui.recipeTitle ?: stringResource(R.string.cooked_overline))
            CardSection(
                footer = stringResource(R.string.cooked_footer),
                rows = ui.usableItems.map { item ->
                    {
                        val selected = item.id in ui.selectedIds
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Checkbox, onValueChange = { viewModel.toggle(item) }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (selected) colors.brandOrange else colors.textMuted,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(item.name, style = ZeroBiteTheme.type.subheadline.medium(), color = colors.textPrimary, modifier = Modifier.weight(1f))
                            Text(item.quantityLabel(), style = ZeroBiteTheme.type.caption, color = colors.textSecondary)
                        }
                    }
                },
            )
        }
        AppButton(
            stringResource(ui.confirmTitle),
            viewModel::confirm,
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm).testTag("cooked.confirm"),
        )
    }
}
