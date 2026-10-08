package com.mazesys.zerobite.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.min

enum class BrandMarkStyle {
    /** Red disc, cream ring, band and Z. */
    COLOR,
    /** Greyscale, for monochrome/themed icons. */
    TINTED,
}

object BrandMarkColors {
    val red = Color(0xFFB23A22)
    val cream = Color(0xFFFFFBF7)
    val ink = Color(0xFF2B1A12)
}

/**
 * The Zero Bite mark: a red disc with a cream ring, a Z whose diagonal runs on as a band (like
 * the UK national speed limit sign), and an apple-style bite on the east side.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, style: BrandMarkStyle = BrandMarkStyle.COLOR) {
    val disc = if (style == BrandMarkStyle.COLOR) BrandMarkColors.red else Color(0xFF6B6B6B)
    val ink = if (style == BrandMarkStyle.COLOR) BrandMarkColors.cream else Color.White
    Canvas(
        modifier
            .aspectRatio(1f)
            // Offscreen so the bite can punch through to whatever is behind the mark.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .semantics { contentDescription = "Zero Bite" },
    ) {
        drawBrandMark(disc, ink)
    }
}

// Geometry in a 1024 × 1024 design space (see design/BrandMark.svg).
private val CENTER = Offset(512f, 512f)
private const val RING_OUTER_RADIUS = 376f
private const val DISC_RADIUS = 300f
/** The Z is drawn in its own 1024 space and scaled to sit inside the ring. */
private const val LETTER_SCALE = 0.909f
private const val BAND_ANGLE_DEGREES = -40f
private val BITE_CENTER = Offset(885f, 464f)
private const val BITE_RADIUS = 175f

fun DrawScope.drawBrandMark(disc: Color, ink: Color) {
    val side = min(size.width, size.height)
    translate(left = (size.width - side) / 2, top = (size.height - side) / 2) {
        scale(side / 1024f, pivot = Offset.Zero) {
            drawCircle(ink, RING_OUTER_RADIUS, CENTER)
            drawCircle(disc, DISC_RADIUS, CENTER)

            scale(LETTER_SCALE, pivot = CENTER) {
                drawRect(ink, Offset(372f, 352f), Size(280f, 84f))
                drawRect(ink, Offset(372f, 588f), Size(280f, 84f))
                // The diagonal runs right through to the outer edge so it fuses with the ring.
                val ring = Path().apply { addOval(Rect(CENTER, RING_OUTER_RADIUS / LETTER_SCALE)) }
                clipPath(ring) {
                    rotate(BAND_ANGLE_DEGREES, pivot = CENTER) {
                        drawRect(ink, Offset(-100f, 470f), Size(1224f, 84f))
                    }
                }
            }

            drawCircle(Color.Black, BITE_RADIUS, BITE_CENTER, blendMode = BlendMode.DstOut)
        }
    }
}
