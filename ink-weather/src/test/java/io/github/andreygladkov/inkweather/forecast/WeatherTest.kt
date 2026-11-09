package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import org.junit.After
import java.io.File
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
        val outcome = assertIs<Weather.Outcome.Updated>(Weather.refresh(context, Fixtures.MOSCOW))
        assertEquals(outcome.forecast, cache.load(Fixtures.MOSCOW))
        assertEquals(Fixtures.NOW, cache.load(Fixtures.MOSCOW)!!.fetchedAt)
    }

    @Test fun noConnectionAndAServiceErrorAreToldApartAndKeepTheOldForecast() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast(current = 7.0) }
        Weather.refresh(context, Fixtures.MOSCOW)

        Weather.transport = Fixtures.offline()
        assertEquals(Weather.Outcome.Failed(Weather.Failure.NETWORK), Weather.refresh(context, Fixtures.MOSCOW))
        Weather.transport = Fixtures.FakeTransport { throw ServiceException("HTTP 502") }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.SERVICE), Weather.refresh(context, Fixtures.MOSCOW))
        Weather.transport = Fixtures.FakeTransport { """{"type":"Feature"}""" }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.SERVICE), Weather.refresh(context, Fixtures.MOSCOW))

        assertEquals(7.0, cache.load(Fixtures.MOSCOW)!!.current(Fixtures.NOW)!!.temperature)
    }

    @Test fun aForecastThatCannotBeSavedIsAStorageFailure() {
        File(context.filesDir, "forecasts").apply { deleteRecursively(); writeText("not a directory") }
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        assertEquals(Weather.Outcome.Failed(Weather.Failure.STORAGE), Weather.refresh(context, Fixtures.MOSCOW))
    }

    @Test fun aSavedForecastIsParsedOnceUntilItChanges() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.refresh(context, Fixtures.MOSCOW)
        val first = cache.load(Fixtures.MOSCOW)
        assertSame(first, cache.load(Fixtures.MOSCOW))
        cache.save(Fixtures.MOSCOW.id, Fixtures.forecast(current = 30.0), Fixtures.NOW)
        assertEquals(30.0, cache.load(Fixtures.MOSCOW)!!.current(Fixtures.NOW)!!.temperature)
    }

    @Test fun aForecastIsFreshForHalfAnHour() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.refresh(context, Fixtures.MOSCOW)
        assertTrue(Weather.isFresh(cache.load(Fixtures.MOSCOW)))
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofMinutes(30)), ZoneOffset.UTC)
        assertFalse(Weather.isFresh(cache.load(Fixtures.MOSCOW)))
        assertFalse(Weather.isFresh(null))
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
        Weather.refresh(context, Fixtures.MOSCOW)
        cache.delete(Fixtures.MOSCOW.id)
        assertNull(cache.load(Fixtures.MOSCOW))
    }
}
