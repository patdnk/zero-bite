package com.mazesys.zerobite.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.medium
import com.mazesys.zerobite.ui.theme.semibold

data class TabItem<T>(val key: T, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

/** Flat full-width tab bar: hairline on top, icon + label, and an indicator that slides along the top. */
@Composable
fun <T> AppTabBar(tabs: List<TabItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = ZeroBiteTheme.colors
    val index = tabs.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    BoxWithConstraints(modifier.fillMaxWidth().background(colors.backgroundPrimary)) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * index + (tabWidth - 44.dp) / 2,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "tabIndicator",
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSelected = tab.key == selected
                val tint = if (isSelected) colors.textPrimary else colors.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .testTag("tab.${tab.title.lowercase()}")
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab.key) })
                        .padding(top = 10.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        tab.title,
                        style = ZeroBiteTheme.type.caption2.let { if (isSelected) it.semibold() else it.medium() },
                        color = tint,
                    )
                }
            }
        }
        Box(Modifier.offset(x = indicatorX).size(width = 44.dp, height = 3.dp).background(colors.brandOrange))
    }
}
