package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.graphics.Color
import com.mazesys.zerobite.core.model.ExpiryStatus

fun ExpiryStatus.tint(colors: ZeroBiteColors): Color = when (this) {
    ExpiryStatus.EXPIRED -> colors.statusExpired
    ExpiryStatus.TODAY -> colors.statusToday
    ExpiryStatus.SOON -> colors.statusSoon
    ExpiryStatus.FRESH -> colors.statusFresh
}
