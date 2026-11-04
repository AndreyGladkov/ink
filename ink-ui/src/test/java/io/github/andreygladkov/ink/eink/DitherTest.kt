package io.github.andreygladkov.ink.eink

import android.graphics.Color
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DitherTest {
    @Test fun blackAndWhiteStayAsTheyAre() {
        assertTrue(Dither.atkinson(IntArray(64) { 0 }, 8, 8).all { it })
        assertTrue(Dither.atkinson(IntArray(64) { 255 }, 8, 8).none { it })
    }

    @Test fun darkerGreyGetsMoreBlackDots() {
        val shares = listOf(64, 128, 192).map { grey -> Dither.atkinson(IntArray(1024) { grey }, 32, 32).count { it } / 1024.0 }
        assertTrue(shares[0] > shares[1] && shares[1] > shares[2], "black share by grey level: $shares")
        assertTrue(shares[1] in 0.3..0.7, "mid grey black share: ${shares[1]}")
    }

    @Test fun luminanceTreatsTransparencyAsThePage() {
        assertEquals(255, Dither.luminance(Color.TRANSPARENT))
        assertEquals(0, Dither.luminance(Color.BLACK))
        assertEquals(255, Dither.luminance(Color.WHITE))
    }

    @Test fun bitmapIsPureBlackAndWhiteAtTheRequestedSize() {
        val photo = createBitmap(40, 30).apply {
            for (x in 0 until 40) for (y in 0 until 30) setPixel(x, y, Color.rgb(x * 6, y * 8, 120))
        }
        val dithered = Dither.bitmap(photo, 20, 15)
        assertEquals(20 to 15, dithered.width to dithered.height)
        assertTrue(dithered.pixels().all { it == Color.BLACK || it == Color.WHITE })
        assertTrue(dithered.pixels().any { it == Color.BLACK } && dithered.pixels().any { it == Color.WHITE })
    }
}
