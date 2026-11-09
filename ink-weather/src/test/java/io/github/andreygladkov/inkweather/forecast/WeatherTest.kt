package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import org.junit.After
import org.json.JSONObject
import java.io.File
import java.net.URI
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WeatherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = ForecastCache(context)

    @Before fun freezeTime() {
        Weather.clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)
    }

    @After fun restore() {
        Weather.transport = HttpTransport
        Weather.clock = Clock.systemDefaultZone()
    }

    @Test fun aRefreshKeepsTheForecastForLater() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        val outcome = assertIs<Weather.Outcome.Updated>(Weather.refresh(context, Fixtures.MOSCOW, Source.of(context)))
        assertEquals(outcome.forecast, cache.load(Fixtures.MOSCOW))
        assertEquals(Fixtures.NOW, cache.load(Fixtures.MOSCOW)!!.fetchedAt)
    }

    @Test fun noConnectionAndAServiceErrorAreToldApartAndKeepTheOldForecast() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast(current = 7.0) }
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))

        Weather.transport = Fixtures.offline()
        assertEquals(Weather.Outcome.Failed(Weather.Failure.NETWORK), Weather.refresh(context, Fixtures.MOSCOW, Source.of(context)))
        Weather.transport = Fixtures.FakeTransport { throw ServiceException("HTTP 502") }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.SERVICE), Weather.refresh(context, Fixtures.MOSCOW, Source.of(context)))
        Weather.transport = Fixtures.FakeTransport { """{"type":"Feature"}""" }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.SERVICE), Weather.refresh(context, Fixtures.MOSCOW, Source.of(context)))

        assertEquals(7.0, cache.load(Fixtures.MOSCOW)!!.current(Fixtures.NOW)!!.temperature)
    }

    @Test fun aForecastThatCannotBeSavedIsAStorageFailure() {
        File(context.filesDir, "forecasts").apply { deleteRecursively(); writeText("not a directory") }
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.STORAGE), Weather.refresh(context, Fixtures.MOSCOW, Source.of(context)))
    }

    @Test fun aSavedForecastIsParsedOnceUntilItChanges() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))
        val first = cache.load(Fixtures.MOSCOW)
        assertSame(first, cache.load(Fixtures.MOSCOW))
        cache.save(Fixtures.MOSCOW.id, Fixtures.forecast(current = 30.0), Fixtures.NOW, Source.MET_NO)
        assertEquals(30.0, cache.load(Fixtures.MOSCOW)!!.current(Fixtures.NOW)!!.temperature)
    }

    @Test fun aForecastSavedBeforeSourcesWereKeptIsReadAsMetNorways() {
        File(context.filesDir, "forecasts").mkdirs()
        File(context.filesDir, "forecasts/${Fixtures.MOSCOW.id}.json")
            .writeText(JSONObject().put("fetchedAt", Fixtures.NOW.toEpochMilli()).put("body", Fixtures.forecast()).toString())
        assertEquals(Source.MET_NO, cache.load(Fixtures.MOSCOW)!!.source)
    }

    @Test fun aForecastIsFreshForHalfAnHour() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))
        assertTrue(Weather.isFresh(cache.load(Fixtures.MOSCOW), Source.MET_NO))
        assertFalse(Weather.isFresh(cache.load(Fixtures.MOSCOW), Source.OPEN_METEO))
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofMinutes(30)), ZoneOffset.UTC)
        assertFalse(Weather.isFresh(cache.load(Fixtures.MOSCOW), Source.MET_NO))
        assertFalse(Weather.isFresh(null, Source.MET_NO))
    }

    @Test fun theChosenSourceIsAskedAndRemembered() {
        val transport = Fixtures.FakeTransport { url -> if ("open-meteo" in url) Fixtures.openMeteo() else Fixtures.forecast() }
        Weather.transport = transport
        assertEquals(Source.MET_NO, Source.of(context))
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))
        Source.choose(context, Source.OPEN_METEO)
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))
        assertEquals(listOf("api.met.no", "api.open-meteo.com"), transport.asked.map { URI(it).host })
        assertEquals(Source.OPEN_METEO, cache.load(Fixtures.MOSCOW)!!.source)
    }

    @Test fun aSearchFindsPlacesOrSaysWhyNot() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS) }
        assertEquals(Weather.Found.Places(listOf(Fixtures.PARIS)), Weather.search("Пар", "ru"))
        Weather.transport = Fixtures.offline()
        assertEquals(Weather.Found.Failed(Weather.Failure.NETWORK), Weather.search("Пар", "ru"))
        Weather.transport = Fixtures.FakeTransport { "not json" }
        assertEquals(Weather.Found.Failed(Weather.Failure.SERVICE), Weather.search("Пар", "ru"))
    }

    @Test fun aDeletedForecastIsGone() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.refresh(context, Fixtures.MOSCOW, Source.of(context))
        cache.delete(Fixtures.MOSCOW.id)
        assertNull(cache.load(Fixtures.MOSCOW))
    }
}
