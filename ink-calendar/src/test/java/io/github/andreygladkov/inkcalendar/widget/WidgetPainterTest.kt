package io.github.andreygladkov.inkcalendar.widget

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.events.Occurrence
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetPainterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density
    private val content = WidgetContent.of(context, Instant.ofEpochMilli(at(TODAY, 8)), ZONE, listOf(
        Occurrence(1, at(TODAY, 9), at(TODAY, 10), false, "Стендап", 1, "", "", "", false),
        Occurrence(2, at(TODAY.plusDays(1), 11), at(TODAY.plusDays(1), 12), false, "Созвон", 1, "", "", "", false),
    ))

    @Test fun everyShapeIsTwoToneAndDrawsSomething() {
        listOf(100f to 73f, 167f to 73f, 300f to 110f, 250f to 320f).forEach { (width, height) ->
            val picture = WidgetPainter(context).draw(content, px(width), px(height))
            assertPureBlackAndWhite(picture)
            assertTrue(picture.pixels().count { it == Color.BLACK } > 100, "$width×$height")
        }
    }

    @Test fun aTallWidgetDrawsTheMonthBelowTheEvents() {
        val short = WidgetPainter(context).draw(content, px(250f), px(110f))
        val tall = WidgetPainter(context).draw(content, px(250f), px(320f))
        val lowerHalf = { picture: android.graphics.Bitmap ->
            (picture.height / 2 until picture.height).sumOf { y -> (0 until picture.width).count { x -> picture.getPixel(x, y) == Color.BLACK } }
        }
        assertTrue(lowerHalf(tall) > lowerHalf(short) * 3)
    }

    private fun px(dp: Float) = (dp * density).roundToInt()
}
