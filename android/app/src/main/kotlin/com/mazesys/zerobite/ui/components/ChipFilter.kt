package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.Radius
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** Horizontally scrolling single-select chips (replaces a segmented control). */
@Composable
fun <T> ChipFilter(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.gutter),
) {
    val colors = ZeroBiteTheme.colors
    val style = ZeroBiteTheme.type.subheadline
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { option ->
            val isSelected = option == selected
            Text(
                label(option),
                style = if (isSelected) style.semibold() else style,
                color = if (isSelected) colors.onInk else colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.control))
                    .background(if (isSelected) colors.textPrimary else colors.chipFill)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
