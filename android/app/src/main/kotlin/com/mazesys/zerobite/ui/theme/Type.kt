package com.mazesys.zerobite.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS Dynamic Type text styles at the default size; `sp` keeps the user's font scale. */
@Immutable
data class ZeroBiteTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
) {
    companion object {
        fun of(family: FontFamily): ZeroBiteTypography {
            fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
                TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)
            return ZeroBiteTypography(
                largeTitle = style(34, 41),
                title = style(28, 34),
                title2 = style(22, 28),
                title3 = style(20, 25),
                headline = style(17, 22, FontWeight.SemiBold),
                body = style(17, 22),
                callout = style(16, 21),
                subheadline = style(15, 20),
                footnote = style(13, 18),
                caption = style(12, 16),
                caption2 = style(11, 13),
            )
        }
    }
}

fun TextStyle.medium(): TextStyle = copy(fontWeight = FontWeight.Medium)
fun TextStyle.semibold(): TextStyle = copy(fontWeight = FontWeight.SemiBold)
fun TextStyle.bold(): TextStyle = copy(fontWeight = FontWeight.Bold)
