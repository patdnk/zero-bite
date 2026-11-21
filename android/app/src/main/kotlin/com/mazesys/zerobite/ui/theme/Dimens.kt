package com.mazesys.zerobite.ui.theme

import androidx.compose.ui.unit.dp

/** Deliberately tight corners, so the app reads as bespoke rather than stock Material. */
object Radius {
    /** Cards, panels, image placeholders. */
    val card = 4.dp
    /** Buttons, chips, inputs, icon tiles. */
    val control = 3.dp
}

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    /** Horizontal page gutter. */
    val gutter = 16.dp
}
