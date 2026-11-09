package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.Fixtures
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class OpenMeteoTest {
    @Test fun aSearchIsEncodedAndAskedInTheGivenLanguage() {
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=%D0%9D%D0%B8%D0%B6%D0%BD%D0%B8%D0%B9+%D0%9D%D0%BE%D0%B2%D0%B3%D0%BE%D1%80%D0%BE%D0%B4&count=10&language=ru&format=json",
            OpenMeteo.searchUrl("  Нижний Новгород ", "ru"))
    }

    @Test fun placesKeepTheirRegionCountryAndTimeZone() {
        assertEquals(listOf(Fixtures.MOSCOW, Fixtures.PARIS), OpenMeteo.places(Fixtures.places(Fixtures.MOSCOW, Fixtures.PARIS)))
        assertEquals(ZoneId.of("Europe/Paris"), OpenMeteo.places(Fixtures.places(Fixtures.PARIS)).single().zone)
        assertEquals(emptyList(), OpenMeteo.places("""{"generationtime_ms":0.5}"""))
    }

    @Test fun aPlaceSavedWithoutATimeZoneUsesThePhones() {
        assertEquals(ZoneId.systemDefault(), Fixtures.MOSCOW.copy(timezone = "").zone)
        assertEquals(ZoneId.of("Europe/Moscow"), Place.fromJson(Fixtures.MOSCOW.toJson()).zone)
    }
}
