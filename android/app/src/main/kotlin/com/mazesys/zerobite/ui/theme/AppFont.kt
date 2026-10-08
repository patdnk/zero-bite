package com.mazesys.zerobite.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Mona Sans when scripts/fetch-fonts.sh has put the variable font into assets, else the system
 * font, so the app always builds and runs (same rule as iOS).
 */
object AppFont {
    private const val FILE = "mona_sans.ttf"
    private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

    fun family(assets: AssetManager): FontFamily =
        resolve(assets.list("fonts")?.toList().orEmpty()) { monaSans(assets) }

    fun resolve(bundledFonts: List<String>, load: () -> FontFamily): FontFamily =
        if (FILE in bundledFonts) load() else FontFamily.Default

    private fun monaSans(assets: AssetManager): FontFamily = FontFamily(
        WEIGHTS.map { weight ->
            Font(
                path = "fonts/$FILE",
                assetManager = assets,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )
}
