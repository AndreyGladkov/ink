package io.github.andreygladkov.inkclock.pomodoro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class TomatoViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun theHighlightStaysVisibleOnBlackAndOnEatenFlesh() {
        val whole = draw(PomodoroDial(25, black = 25, refills = false))
        val eaten = draw(PomodoroDial(25, black = 0, refills = false))
        assertPureBlackAndWhite(whole)
        assertPureBlackAndWhite(eaten)
        val art = ArtFrame(eaten)
        assertEquals(Color.WHITE, art.pixel(whole, HIGHLIGHT_X, HIGHLIGHT_Y))
        assertEquals(Color.BLACK, art.pixel(eaten, HIGHLIGHT_X, HIGHLIGHT_Y))
        assertEquals(Color.BLACK, art.pixel(whole, FLESH_X, FLESH_Y))
        assertEquals(Color.WHITE, art.pixel(eaten, FLESH_X, FLESH_Y))
    }

    private fun draw(dial: PomodoroDial): Bitmap {
        val view = TomatoView(context).apply {
            setBackgroundColor(Color.WHITE)
            this.dial = dial
        }
        return render(view, WIDTH, HEIGHT)
    }

    /**
     * Maps tomato.xml units to pixels from the black extent of a drawing: the body spans x 2.8–21.2 and the stem tops
     * out at y 5.6. The outline is stroked on the body's edge, so half of its width, read where the body is widest,
     * lies outside that span.
     */
    private class ArtFrame(drawing: Bitmap) {
        private val left: Int
        private val top: Int
        private val scale: Float

        init {
            val pixels = drawing.pixels()
            val black = pixels.indices.filter { pixels[it] == Color.BLACK }
            val xs = black.map { it % drawing.width }
            val widest = black.first { it % drawing.width == xs.min() } / drawing.width
            val outline = (xs.min() until drawing.width).takeWhile { pixels[widest * drawing.width + it] == Color.BLACK }.size
            left = xs.min() + outline / 2
            top = black.minOf { it / drawing.width }
            scale = (xs.max() - outline / 2 - left).toFloat() / (BODY_RIGHT - BODY_LEFT)
        }

        fun pixel(bitmap: Bitmap, unitX: Float, unitY: Float) =
            bitmap.getPixel(left + ((unitX - BODY_LEFT) * scale).roundToInt(), top + ((unitY - STEM_TOP) * scale).roundToInt())
    }

    private companion object {
        const val WIDTH = 740
        const val HEIGHT = 700
        const val BODY_LEFT = 2.8f
        const val BODY_RIGHT = 21.2f
        const val STEM_TOP = 5.6f
        const val HIGHLIGHT_X = 5.8f
        const val HIGHLIGHT_Y = 11.6f
        const val FLESH_X = 12f
        const val FLESH_Y = 17f
    }
}
