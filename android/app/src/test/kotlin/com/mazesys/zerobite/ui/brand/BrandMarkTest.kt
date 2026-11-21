package com.mazesys.zerobite.ui.brand

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
class BrandMarkTest {

    @get:Rule
    val compose = createComposeRule()

    private fun render(style: BrandMarkStyle = BrandMarkStyle.COLOR): android.graphics.Bitmap {
        compose.setContent {
            Box(Modifier.size(256.dp).background(Color.White).testTag("mark")) {
                BrandMark(Modifier.fillMaxSize(), style)
            }
        }
        return compose.onNodeWithTag("mark").captureToImage().asAndroidBitmap()
    }

    private fun channelDiff(a: Int, b: Int): Int =
        listOf(16, 8, 0).maxOf { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) }

    @Test
    fun `matches the iOS rendering of the mark`() {
        val actual = render()
        val reference = File(System.getProperty("zerobite.testdata"), "brandmark-256.png")
        val expected = BitmapFactory.decodeFile(reference.path)
        assertEquals(256, actual.width)
        var differing = 0
        for (y in 0 until 256) for (x in 0 until 256) {
            if (channelDiff(actual.getPixel(x, y), expected.getPixel(x, y)) > 48) differing++
        }
        // Only anti-aliased edges may differ between the two renderers.
        assertTrue(differing < 256 * 256 / 100, "$differing of 65536 pixels differ from the iOS mark")
    }

    @Test
    fun `tinted style is a grey disc`() {
        val pixel = render(BrandMarkStyle.TINTED).getPixel(128, 66) // inside the disc, clear of the Z
        assertTrue(channelDiff(pixel, 0xFF6B6B6B.toInt()) <= 2, Integer.toHexString(pixel))
    }

    @Test
    fun `is labelled for accessibility`() {
        compose.setContent { BrandMark(Modifier.size(64.dp)) }
        compose.onNodeWithContentDescription("Zero Bite").assertExists()
    }
}
