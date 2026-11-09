package io.github.andreygladkov.inkweather.widget

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.findInSearch
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.places.SearchActivity
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetConfigActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PlaceStore.of(context)
    private val widgetId = 5

    @Before fun bind() {
        Weather.clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(widgetId, ComponentName(context, WeatherWidget::class.java))
    }

    @After fun restore() {
        Weather.transport = HttpTransport
        Weather.clock = Clock.systemDefaultZone()
    }

    @Test fun choosingACityBindsTheWidgetFetchesItsForecastAndDrawsIt() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        val activity = open()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        contentRoot(activity).find<InkButton>().single { it.text.startsWith("Париж") }.performClick()

        assertEquals(Fixtures.PARIS, store.widgetPlace(widgetId))
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(widgetId, shadowOf(activity).resultIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        idleUntil { ForecastCache(context).load(Fixtures.PARIS) != null && store.drawn(widgetId) != null }
        assertTrue(shadowOf(AppWidgetManager.getInstance(context)).getViewFor(widgetId).contentDescription.contains("Париж"))
    }

    @Test fun reconfiguringMarksTheCityShownNow() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        store.bind(widgetId, Fixtures.PARIS)
        val buttons = contentRoot(open()).find<InkButton>().filter { '\n' in it.text }
        assertEquals(listOf(false, true), buttons.map { it.isSelected })
    }

    @Test fun withoutCitiesTheSearchOpensAtOnceAndItsCityIsTaken() {
        Weather.transport = Fixtures.FakeTransport { url -> if ("geocoding" in url) Fixtures.places(Fixtures.MOSCOW) else Fixtures.forecast() }
        val activity = open()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(SearchActivity::class.java.name, asked.intent.component?.className)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, findInSearch(asked.intent, Fixtures.MOSCOW))
        assertEquals(Fixtures.MOSCOW, store.widgetPlace(widgetId))
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertTrue(activity.isFinishing)
    }

    @Test fun cancellingTheFirstSearchLeavesTheWidgetUnplaced() {
        val activity = open()
        val search = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(search.intent, Activity.RESULT_CANCELED, null)
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(null, store.widgetPlace(widgetId))
    }

    private fun open() = Robolectric.buildActivity(WidgetConfigActivity::class.java,
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)).setup().get()
}
