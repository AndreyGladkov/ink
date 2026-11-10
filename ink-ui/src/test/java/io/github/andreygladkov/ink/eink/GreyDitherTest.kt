package io.github.andreygladkov.ink.eink

import android.graphics.Color
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.pixels
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GreyDitherTest {
    private fun flat(color: Int, size: Int = 64) = createBitmap(size, size).apply { eraseColor(color) }

    @Test fun everyThresholdOccursEquallyOften() {
        val counts = BlueNoise.thresholds.asList().groupingBy { it.toInt() and 0xFF }.eachCount()
        assertEquals((0..255).toSet(), counts.keys)
        assertEquals(setOf(BlueNoise.SIZE * BlueNoise.SIZE / 256), counts.values.toSet())
    }

    @Test fun blackWhiteAndThePanelsOwnGreysStayAsTheyAre() {
        (0 until GreyDither.LEVELS).map { it * GreyDither.STEP }.forEach { grey ->
            val bitmap = flat(Color.rgb(grey, grey, grey))
            GreyDither.quantize(bitmap)
            assertEquals(setOf(Color.rgb(grey, grey, grey)), bitmap.pixels().toSet(), "grey $grey")
        }
    }

    @Test fun aGreyBetweenTwoLevelsMixesOnlyThoseTwoAndKeepsItsBrightness() {
        val bitmap = flat(Color.rgb(100, 100, 100))
        GreyDither.quantize(bitmap)
        val greys = bitmap.pixels().map { Color.red(it) }
        assertEquals(setOf(85, 102), greys.toSet())
        assertTrue(abs(greys.average() - 100) < 1, "average ${greys.average()}")
    }

    @Test fun transparencyShowsWhitePage() {
        val bitmap = flat(Color.TRANSPARENT)
        GreyDither.quantize(bitmap)
        assertEquals(setOf(Color.WHITE), bitmap.pixels().toSet())
    }

    @Test fun aGradientUsesEveryLevelInOrder() {
        val bitmap = createBitmap(256, 64).apply {
            for (x in 0 until 256) for (y in 0 until 64) setPixel(x, y, Color.rgb(x, x, x))
        }
        GreyDither.quantize(bitmap)
        val columnMeans = (0 until 256).map { x -> (0 until 64).map { Color.red(bitmap.getPixel(x, it)) }.average() }
        assertEquals((0 until GreyDither.LEVELS).map { it * GreyDither.STEP }.toSet(), bitmap.pixels().map(Color::red).toSet())
        columnMeans.forEachIndexed { x, mean -> assertTrue(abs(mean - x) < 4, "column $x averages $mean") }
    }
}
