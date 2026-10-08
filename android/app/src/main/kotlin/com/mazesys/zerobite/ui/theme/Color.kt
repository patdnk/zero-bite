package com.mazesys.zerobite.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colour tokens: the iOS `Constants` palette (warm, darkened red/orange/yellow) in light and dark. */
@Immutable
data class ZeroBiteColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val cardTint: Color,
    /** Card and input fill. */
    val surface: Color,
    /** 1dp card, input and tab bar borders. */
    val hairline: Color,
    /** Unselected chips, secondary buttons, empty progress tracks. */
    val chipFill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Text on an ink fill (selected chips, primary buttons). */
    val onInk: Color,
    val brand: Color,
    val brandOrange: Color,
    val brandYellow: Color,
    val statusExpired: Color,
    val statusToday: Color,
    val statusSoon: Color,
    /** Muted olive, so "fine" stays distinguishable from the warning ramp. */
    val statusFresh: Color,
)

val LightColors = ZeroBiteColors(
    backgroundPrimary = Color(0xFFFFFBF7),
    backgroundSecondary = Color(0xFFF6EEE6),
    cardTint = Color(0xFFF3E1D2),
    surface = Color(0xFFFFFFFF),
    hairline = Color(0xFFEEE1D5),
    chipFill = Color(0xFFF6EEE6),
    textPrimary = Color(0xFF2B1A12),
    textSecondary = Color(0xFF7A5C4E),
    textMuted = Color(0xFFA08779),
    onInk = Color(0xFFFFFBF7),
    brand = Color(0xFFB23A22),
    brandOrange = Color(0xFFC2611A),
    brandYellow = Color(0xFFC2941A),
    statusExpired = Color(0xFF9B1C1C),
    statusToday = Color(0xFFC2611A),
    statusSoon = Color(0xFFC2941A),
    statusFresh = Color(0xFF6E7F1E),
)

val DarkColors = ZeroBiteColors(
    backgroundPrimary = Color(0xFF201816),
    backgroundSecondary = Color(0xFF140F0D),
    cardTint = Color(0xFF2C201A),
    surface = Color(0xFF2A201C),
    hairline = Color(0xFF3A2C25),
    chipFill = Color(0xFF352821),
    textPrimary = Color(0xFFF7F0EA),
    textSecondary = Color(0xFFC3B2A8),
    textMuted = Color(0xFF8F7D73),
    onInk = Color(0xFF201816),
    brand = Color(0xFFD9573B),
    brandOrange = Color(0xFFE07B2E),
    brandYellow = Color(0xFFE0B33A),
    statusExpired = Color(0xFFE24B4B),
    statusToday = Color(0xFFE07B2E),
    statusSoon = Color(0xFFE0B33A),
    statusFresh = Color(0xFF9DB33A),
)
