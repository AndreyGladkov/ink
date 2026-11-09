package io.github.andreygladkov.inkweather.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.SizeF
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.MainActivity
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.forecast.Source
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetUpdaterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = AppWidgetManager.getInstance(context)
    private val store = PlaceStore.of(context)
    private var lastId = 0

    @Before fun seed() {
        Weather.clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)
        ForecastCache(context).save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
    }

    @After fun restore() {
        Weather.transport = HttpTransport
        Weather.clock = Clock.systemDefaultZone()
    }

    @Test fun aWidgetIsDrawnAtTheSizeTheLauncherGaveIt() {
        val id = widget(183f, 95f)
        WidgetUpdater.update(context, intArrayOf(id), force = true)
        val bitmap = bitmap(id)
        val density = context.resources.displayMetrics.density
        assertEquals((183f * density).roundToInt() to (95f * density).roundToInt(), bitmap.width to bitmap.height)
        assertPureBlackAndWhite(bitmap)
        assertTrue("Москва" in shadowOf(manager).getViewFor(id).contentDescription)
    }

    @Test fun anUnchangedWidgetIsNotSentToThePanelAgain() {
        val id = widget(366f, 95f)
        WidgetUpdater.updateAll(context, force = true)
        val first = bitmap(id)
        WidgetUpdater.updateAll(context)
        assertSame(first, bitmap(id))

        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        WidgetUpdater.updateAll(context)
        assertNotSame(first, bitmap(id))
    }

    @Test fun aNewSizeIsDrawnAgain() {
        val id = widget(183f, 95f)
        WidgetUpdater.update(context, intArrayOf(id), force = true)
        manager.updateAppWidgetOptions(id, sizes(366f, 190f))
        WidgetUpdater.update(context, intArrayOf(id), force = false)
        assertEquals((366f * context.resources.displayMetrics.density).roundToInt(), bitmap(id).width)
    }

    @Test fun onlyTheWidgetsOfARefreshedPlaceAreLookedAt() {
        val moscow = widget(183f, 95f)
        val paris = widget(183f, 95f, Fixtures.PARIS)
        WidgetUpdater.updateAll(context, force = true)
        val moscowBefore = bitmap(moscow)
        val parisBefore = bitmap(paris)
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach { ForecastCache(context).save(it.id, Fixtures.forecast(current = 12.0), Fixtures.NOW, Source.MET_NO) }
        WidgetUpdater.updateFor(context, Fixtures.MOSCOW.id)
        assertNotSame(moscowBefore, bitmap(moscow))
        assertSame(parisBefore, bitmap(paris))
    }

    @Test fun aTapOpensTheAppOnTheWidgetsPlace() {
        store.add(Fixtures.PARIS)
        store.add(Fixtures.MOSCOW)
        val id = widget(183f, 95f)
        WidgetUpdater.update(context, intArrayOf(id), force = true)
        shadowOf(manager).getViewFor(id).performClick()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        Robolectric.buildActivity(MainActivity::class.java, started).setup()
        assertEquals(Fixtures.MOSCOW, store.current)
    }

    private fun widget(widthDp: Float, heightDp: Float, place: Place = Fixtures.MOSCOW): Int {
        val id = ++lastId
        shadowOf(manager).bindAppWidgetId(id, ComponentName(context, WeatherWidget::class.java))
        manager.updateAppWidgetOptions(id, sizes(widthDp, heightDp))
        store.bind(id, place)
        return id
    }

    private fun sizes(widthDp: Float, heightDp: Float) = Bundle().apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(widthDp, heightDp)))
    }

    private fun bitmap(id: Int): Bitmap = ((shadowOf(manager).getViewFor(id) as ImageView).drawable as BitmapDrawable).bitmap
}
