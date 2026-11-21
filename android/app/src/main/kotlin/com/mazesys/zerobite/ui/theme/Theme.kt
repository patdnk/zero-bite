package com.mazesys.zerobite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

private val LocalColors = staticCompositionLocalOf { LightColors }
private val LocalTypography = staticCompositionLocalOf { ZeroBiteTypography.of(FontFamily.Default) }

@Composable
fun ZeroBiteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val family = remember(context) { AppFont.family(context.assets) }
    val typography = remember(family) { ZeroBiteTypography.of(family) }
    val colors = if (darkTheme) DarkColors else LightColors

    // Material 3 is only underneath for dialogs and the date picker; give it our palette and corners.
    MaterialTheme(
        colorScheme = colors.materialScheme(darkTheme),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(Radius.control),
            small = RoundedCornerShape(Radius.control),
            medium = RoundedCornerShape(Radius.card),
            large = RoundedCornerShape(Radius.card),
            extraLarge = RoundedCornerShape(Radius.card),
        ),
        typography = Typography(
            bodyLarge = typography.body,
            bodyMedium = typography.subheadline,
            bodySmall = typography.caption,
            titleLarge = typography.title3,
            titleMedium = typography.headline,
            labelLarge = typography.subheadline.semibold(),
            headlineSmall = typography.title2,
        ),
    ) {
        CompositionLocalProvider(
            LocalColors provides colors,
            LocalTypography provides typography,
            LocalContentColor provides colors.textPrimary,
            LocalTextStyle provides typography.body,
            content = content,
        )
    }
}

object ZeroBiteTheme {
    val colors: ZeroBiteColors
        @Composable @ReadOnlyComposable get() = LocalColors.current

    val type: ZeroBiteTypography
        @Composable @ReadOnlyComposable get() = LocalTypography.current
}

private fun ZeroBiteColors.materialScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = textPrimary,
        onPrimary = onInk,
        secondary = brandOrange,
        onSecondary = Color.White,
        background = backgroundPrimary,
        onBackground = textPrimary,
        surface = backgroundPrimary,
        onSurface = textPrimary,
        surfaceVariant = chipFill,
        onSurfaceVariant = textSecondary,
        surfaceContainerHigh = backgroundPrimary,
        surfaceContainerHighest = surface,
        outline = hairline,
        outlineVariant = hairline,
        error = statusExpired,
    )
}
