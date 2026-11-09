package io.github.andreygladkov.inkweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.forecast.MetNo
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetPainterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val format = WeatherFormat(context, fahrenheit = false, zone = ZoneOffset.ofHours(3))
    private val forecast = MetNo.forecast(Fixtures.forecast(), Fixtures.NOW, Fixtures.MOSCOW.zone)
    private val content = WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW, format)
    private val density = context.resources.displayMetrics.density

    @Test fun cellsOnTheA9ChooseTheShape() {
        assertEquals(WidgetPainter.Shape.SMALL, WidgetPainter.Shape.of(91f, 95f))
        assertEquals(WidgetPainter.Shape.MEDIUM, WidgetPainter.Shape.of(183f, 95f))
        assertEquals(WidgetPainter.Shape.WIDE, WidgetPainter.Shape.of(366f, 95f))
        assertEquals(WidgetPainter.Shape.TALL, WidgetPainter.Shape.of(366f, 190f))
    }

    @Test fun everyShapeIsDrawnInPureBlackAndWhite() {
        listOf(91f to 95f, 183f to 95f, 275f to 95f, 366f to 95f, 366f to 190f, 366f to 285f).forEach { (width, height) ->
            val bitmap = draw(content, width, height)
            assertPureBlackAndWhite(bitmap)
            assertTrue(black(bitmap) > 0.02f, "${width}x$height is almost empty")
        }
    }

    @Test fun aWiderWidgetShowsMore() {
        val inked = listOf(183f to 95f, 366f to 95f, 366f to 190f).map { (width, height) -> black(draw(content, width, height)) * width * height }
        assertTrue(inked[0] < inked[1] && inked[1] < inked[2], "$inked")
    }

    @Test fun theLargestTextStillDrawsInBlackAndWhite() {
        val large = WidgetPainter(context, InkStyle(textSizeSp = InkStyle.TEXT_SIZES_SP.max(), weight = InkStyle.BOLD_WEIGHT))
        listOf(91f to 95f, 183f to 95f, 366f to 190f).forEach { (width, height) ->
            assertPureBlackAndWhite(large.draw(content, px(width), px(height)))
        }
    }

    @Test fun whiteOnBlackFollowsTheAppsColors() {
        val inverted = WidgetPainter(context, InkStyle(inverted = true)).draw(content, px(183f), px(95f))
        assertPureBlackAndWhite(inverted)
        assertEquals(Color.BLACK, inverted.getPixel(1, 1))
    }

    @Test fun aStaleOrMissingForecastIsStillDrawn() {
        val stale = WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW.plus(Duration.ofHours(5)), format)
        assertPureBlackAndWhite(draw(stale, 183f, 95f))
        val missing = draw(WidgetContent.of(Fixtures.MOSCOW, null, Fixtures.NOW, format), 183f, 95f)
        assertPureBlackAndWhite(missing)
        assertTrue(black(missing) > 0.01f)
    }

    private fun draw(content: WidgetContent, widthDp: Float, heightDp: Float) = WidgetPainter(context).draw(content, px(widthDp), px(heightDp))

    private fun px(dp: Float) = (dp * density).roundToInt()

    private fun black(bitmap: Bitmap) = bitmap.pixels().count { it == Color.BLACK }.toFloat() / (bitmap.width * bitmap.height)
}
