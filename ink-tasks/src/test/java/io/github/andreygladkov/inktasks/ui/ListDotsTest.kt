package io.github.andreygladkov.inktasks.ui

import android.app.Application
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.A9_WIDTH
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ListDotsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun theOpenListHasTheLargeDotAndTheOthersSmallOnesCentred() {
        val runs = runs(dots(1, 3), A9_WIDTH)
        assertEquals(3, runs.size)
        assertTrue(runs[1].count() > runs[0].count() && runs[1].count() > runs[2].count())
        assertTrue(abs(A9_WIDTH / 2 - runs[1].center()) <= 1)
        assertTrue(abs((runs[1].center() - runs[0].center()) - (runs[2].center() - runs[1].center())) <= 1)
        assertEquals("Список 2 из 3", dots(1, 3).contentDescription)
    }

    @Test fun manyListsDrawCloserTogetherAndAreLeftOutOnceTheyWouldTouch() {
        val wide = runs(dots(0, 3), A9_WIDTH)
        val narrow = runs(dots(0, 3), NARROW)
        assertEquals(3, narrow.size)
        assertTrue(narrow[1].center() - narrow[0].center() < wide[1].center() - wide[0].center())
        assertTrue(narrow.first().first >= 0 && narrow.last().last < NARROW)

        val tooMany = dots(0, 60)
        assertEquals(0, height(tooMany, A9_WIDTH))
        assertEquals(emptyList(), runs(tooMany, A9_WIDTH))
    }

    @Test fun nothingIsShownForOneListOrOneThatIsNotAmongThem() {
        assertEquals(0, height(dots(0, 1), A9_WIDTH))
        assertEquals(0, height(dots(-1, 3), A9_WIDTH))
        assertEquals(null, dots(-1, 3).contentDescription)
    }

    private fun dots(index: Int, count: Int) = ListDots(context).apply {
        setBackgroundColor(Color.WHITE)
        show(index, count)
    }

    private fun height(view: View, width: Int): Int {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return view.measuredHeight
    }

    /** The black stretches along the row with the most black, which runs through the dots' centres. */
    private fun runs(view: View, width: Int): List<IntRange> {
        val height = height(view, width).coerceAtLeast(1)
        val bitmap = render(view, width, height)
        assertPureBlackAndWhite(bitmap)
        val y = (0 until height).maxBy { row -> (0 until width).count { bitmap.getPixel(it, row) == Color.BLACK } }
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (x in 0..width) {
            val black = x < width && bitmap.getPixel(x, y) == Color.BLACK
            if (black && start < 0) start = x
            if (!black && start >= 0) {
                runs += start until x
                start = -1
            }
        }
        return runs
    }

    private fun IntRange.center() = (first + last) / 2

    private companion object {
        const val NARROW = 80
    }
}
