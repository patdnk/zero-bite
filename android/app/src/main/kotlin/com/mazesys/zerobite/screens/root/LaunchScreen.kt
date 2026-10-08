package com.mazesys.zerobite.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazesys.zerobite.ui.brand.BrandMark
import com.mazesys.zerobite.ui.theme.Spacing
import com.mazesys.zerobite.ui.theme.ZeroBiteTheme
import com.mazesys.zerobite.ui.theme.semibold
import kotlinx.coroutines.delay

/** The mark and name for half a second after the system splash, as on iOS. */
@Composable
fun LaunchScreen(onFinish: () -> Unit) {
    Box(Modifier.fillMaxSize().background(ZeroBiteTheme.colors.backgroundPrimary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            BrandMark(Modifier.size(112.dp))
            Text("Zero Bite", style = ZeroBiteTheme.type.largeTitle.semibold(), letterSpacing = (-1).sp, color = ZeroBiteTheme.colors.textPrimary)
        }
    }
    LaunchedEffect(Unit) {
        delay(500)
        onFinish()
    }
}
