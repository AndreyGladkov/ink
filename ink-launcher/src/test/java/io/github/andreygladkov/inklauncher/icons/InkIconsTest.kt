package io.github.andreygladkov.inklauncher.icons

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkIconsTest {
    private fun ink(pixels: IntArray) = pixels.count { it == Color.BLACK }.toFloat() / pixels.size

    private fun assertMask(pixels: IntArray) =
        assertTrue(pixels.all { it == Color.BLACK || it == Color.TRANSPARENT }, "not a two-tone mask")

    @Test fun aGreyIconIsDitheredToAMaskOfAboutItsTone() {
        val icon = InkIcons.dithered(ColorDrawable(Color.rgb(128, 128, 128)), 64)
        val pixels = icon.pixels()
        assertMask(pixels)
        assertTrue(ink(pixels) in 0.4f..0.6f, "${ink(pixels)}")
    }

    @Test fun aWhiteIconAppearsOnlyWhenInverted() {
        val white = ShapeDrawable(OvalShape()).apply { paint.color = Color.WHITE }
        assertEquals(0f, ink(InkIcons.dithered(white, 64).pixels()))
        val inverted = InkIcons.dithered(white, 64, invert = true).pixels()
        assertMask(inverted)
        assertTrue(ink(inverted) > 0.5f, "${ink(inverted)}")
    }

    @Test fun theThemedLayerIsCroppedToTheVisibleMiddle() {
        val adaptive = AdaptiveIconDrawable(ColorDrawable(Color.RED), null, ColorDrawable(Color.BLACK))
        val layer = assertNotNull(InkIcons.monochromeLayer(adaptive, 48)).pixels()
        assertMask(layer)
        assertEquals(1f, ink(layer))
        assertNull(InkIcons.monochromeLayer(ColorDrawable(Color.BLACK), 48))
    }

    @Test fun aTextIconIsAFramedGlyph() {
        val pixels = InkIcons.text("M", 96, { Typeface.DEFAULT_BOLD }, 4, 8f).pixels()
        assertMask(pixels)
        assertEquals(Color.BLACK, pixels[96 * 48 + 1])
        assertTrue(ink(pixels) in 0.1f..0.6f, "${ink(pixels)}")
    }

    @Test fun aFramedIconKeepsItsPictureInsideTheFrame() {
        val inner = InkIcons.dithered(ColorDrawable(Color.BLACK), InkIcons.framedSize(96, 4))
        val pixels = InkIcons.framed(96, 4, 8f, inner).pixels()
        assertMask(pixels)
        assertEquals(Color.BLACK, pixels[96 * 48 + 1])
        assertEquals(Color.TRANSPARENT, pixels[96 * 48 + 6])
        assertEquals(Color.BLACK, pixels[96 * 48 + 48])
    }

    @Test fun aFolderShowsUpToFourMinis() {
        val mini = InkIcons.folderCellSize(96, 4)
        val full = InkIcons.dithered(ColorDrawable(Color.BLACK), mini)
        val one = ink(InkIcons.folder(96, 4, 8f, listOf(full)).pixels())
        val four = ink(InkIcons.folder(96, 4, 8f, List(5) { full }).pixels())
        assertTrue(four > one * 2, "$one → $four")
    }
}
