package io.github.andreygladkov.inkweather

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.descendants
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.places.SearchActivity
import io.github.andreygladkov.inkweather.ui.PlaceBar
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PlaceStore.of(context)
    private val cache = ForecastCache(context)

    @Before fun freezeTime() {
        Weather.clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)
        Weather.transport = Fixtures.offline()
    }

    @After fun restore() {
        Weather.transport = HttpTransport
        Weather.clock = Clock.systemDefaultZone()
    }

    @Test fun theForecastFillsOneScreenInBlackAndWhite() {
        seed(Fixtures.MOSCOW)
        val activity = open()
        render(contentRoot(activity))
        val texts = texts(activity)
        listOf("Москва", "−3°", "Пасмурно", "Ощущается как −8°", "Ветер 4 м/с, СЗ", "Давление 760 мм рт. ст.", "Осадки 0,4 мм", "Сегодня", "Вт 10", "Данные: MET Norway, Open-Meteo")
            .forEach { assertTrue(it in texts, "$it not in $texts") }
        assertEquals(listOf("15", "18", "21", "0", "3", "6", "9", "12"), texts.filter { it.toIntOrNull() in 0..23 })
        assertTrue("Обновлено в 14:20" !in texts)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun everythingFitsAtTheLargestTextSize() {
        context.inkPrefs().edit().putFloat(InkStyle.PREF_TEXT_SIZE_SP, InkStyle.TEXT_SIZES_SP.max()).commit()
        seed(Fixtures.MOSCOW)
        val activity = open()
        val root = contentRoot(activity)
        render(root)
        val bottom = root.height - root.paddingBottom
        descendants(root).filterIsInstance<TextView>().filter { it.isShown && it.text.isNotEmpty() }.forEach { view ->
            val location = IntArray(2).also(view::getLocationInWindow)
            assertTrue(location[1] + view.height <= bottom, "'${view.text}' ends at ${location[1] + view.height}, below $bottom")
            assertTrue(view.height >= view.paddingTop + view.layout.height + view.paddingBottom, "'${view.text}' is cut")
        }
    }

    @Test fun anOldForecastSaysWhenItWasFetched() {
        seed(Fixtures.MOSCOW)
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofHours(4)), ZoneOffset.UTC)
        assertTrue("Обновлено в 14:20" in texts(open()))
    }

    @Test fun aStaleForecastIsRefreshedAndRedrawn() {
        seed(Fixtures.MOSCOW)
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofMinutes(35)), ZoneOffset.UTC)
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast(current = 21.0) }
        val activity = open()
        idleUntil { "21°" in texts(activity) }
        assertTrue("Обновлено в 14:20" !in texts(activity))
    }

    @Test fun withoutAForecastTheReasonIsShownWithARetry() {
        store.add(Fixtures.MOSCOW)
        val activity = open()
        idleUntil { "Нет сети" in texts(activity) }
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        contentRoot(activity).find<InkButton>().single { it.text.toString() == "Повторить" }.performClick()
        idleUntil { "−3°" in texts(activity) }
    }

    @Test fun aServiceErrorIsToldFromNoConnection() {
        store.add(Fixtures.MOSCOW)
        Weather.transport = Fixtures.FakeTransport { "{}" }
        val activity = open()
        idleUntil { "Сервис погоды не ответил" in texts(activity) }
    }

    @Test fun theArrowsMoveBetweenCitiesAndTheBarHidesForOne() {
        seed(Fixtures.MOSCOW)
        val single = open()
        assertEquals(View.GONE, contentRoot(single).find<PlaceBar>().single().visibility)

        seed(Fixtures.PARIS)
        val activity = open()
        val bar = contentRoot(activity).find<PlaceBar>().single()
        val prev = bar.find<InkButton>().single { it.contentDescription == "Предыдущий город" }
        val next = bar.find<InkButton>().single { it.contentDescription == "Следующий город" }
        assertEquals(View.INVISIBLE, prev.visibility)
        next.performClick()
        assertTrue("Париж" in texts(activity))
        assertEquals(View.INVISIBLE, next.visibility)
        assertEquals(Fixtures.PARIS, PlaceStore.of(context).current)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aForecastThatHasRunOutAndCannotBeRefreshedOffersARetry() {
        seed(Fixtures.MOSCOW)
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofDays(30)), ZoneOffset.UTC)
        val activity = open()
        idleUntil { "Нет сети" in texts(activity) }
        assertTrue(contentRoot(activity).find<InkButton>().any { it.text.toString() == "Повторить" })
    }

    @Test fun aTapOnTheWidgetOfACityDeletedFromTheListPutsItBack() {
        seed(Fixtures.MOSCOW)
        cache.save(Fixtures.PARIS.id, Fixtures.forecast(), Fixtures.NOW)
        store.bind(4, Fixtures.PARIS)
        val activity = Robolectric.buildActivity(MainActivity::class.java, MainActivity.intent(context, Fixtures.PARIS.id, 4)).setup().get()
        assertTrue("Париж" in texts(activity))
        assertEquals(listOf(Fixtures.MOSCOW, Fixtures.PARIS), store.places)
    }

    @Test fun anIntentNamingAPlaceShowsIt() {
        seed(Fixtures.MOSCOW)
        seed(Fixtures.PARIS)
        val activity = Robolectric.buildActivity(MainActivity::class.java, MainActivity.intent(context, Fixtures.PARIS.id)).setup().get()
        assertTrue("Париж" in texts(activity))
    }

    @Test fun withoutCitiesTheSearchOpensAndCancellingItCloses() {
        val activity = open()
        val search = shadowOf(activity).nextStartedActivityForResult
        assertEquals(SearchActivity::class.java.name, search.intent.component?.className)
        shadowOf(activity).receiveResult(search.intent, Activity.RESULT_CANCELED, null)
        assertTrue(activity.isFinishing)
    }

    @Test fun aCityFoundOnFirstLaunchIsShown() {
        Weather.transport = Fixtures.FakeTransport { url -> if ("geocoding" in url) Fixtures.places(Fixtures.MOSCOW, Fixtures.PARIS) else Fixtures.forecast() }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val asked = shadowOf(activity).nextStartedActivityForResult
        controller.pause()
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, findInSearch(asked.intent, Fixtures.PARIS))
        controller.resume()
        idleUntil { "Париж" in texts(activity) && "−3°" in texts(activity) }
    }

    private fun seed(place: Place) {
        store.add(place)
        cache.save(place.id, Fixtures.forecast(), Fixtures.NOW)
    }

    private fun open(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun texts(activity: MainActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
}
