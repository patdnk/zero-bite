package com.mazesys.zerobite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold

/** The mark next to the "Zero Bite" name, in the app typeface. */
@Composable
fun BrandLockup(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Zero Bite" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size * 0.32f),
    ) {
        BrandMark(Modifier.size(size))
        Text("Zero Bite", style = ZeroBiteTheme.type.title3.semibold(), letterSpacing = (-0.4).sp, color = ZeroBiteTheme.colors.textPrimary)
    }
}
