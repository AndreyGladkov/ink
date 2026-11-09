package io.github.andreygladkov.inkweather.settings

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Source
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.widget.WeatherWidget
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After fun restore() {
        Weather.transport = HttpTransport
    }

    @Test fun tappingTheChosenSourceFetchesNothing() {
        val transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.transport = transport
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(2, ComponentName(context, WeatherWidget::class.java))
        PlaceStore.of(context).bind(2, Fixtures.MOSCOW)
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        contentRoot(activity).find<InkButton>().single { it.text.toString() == "MET Norway" }.performClick()
        Weather.fetches.submit {}.get()
        assertTrue(transport.asked.isEmpty())
    }

    @Test fun choosingASourceKeepsItAndRefreshesTheWidgetsFromIt() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.openMeteo() }
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(2, ComponentName(context, WeatherWidget::class.java))
        PlaceStore.of(context).bind(2, Fixtures.MOSCOW)
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val choices = contentRoot(activity).find<InkButton>().filter { it.text.toString() in Source.entries.map(Source::title) }
        assertEquals(listOf("MET Norway" to true, "Open-Meteo" to false), choices.map { it.text.toString() to it.isSelected })
        assertPureBlackAndWhite(render(contentRoot(activity)))

        choices[1].performClick()
        assertEquals(Source.OPEN_METEO, Source.of(context))
        idleUntil { ForecastCache(context).load(Fixtures.MOSCOW)?.source == Source.OPEN_METEO && PlaceStore.of(context).drawn(2) != null }
    }
}
