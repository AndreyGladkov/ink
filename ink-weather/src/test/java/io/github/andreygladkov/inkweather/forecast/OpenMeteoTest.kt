package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.Fixtures
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test fun theForecastIsAskedForInUnixTimeAndMetresPerSecond() {
        val url = OpenMeteo.forecastUrl(Fixtures.MOSCOW)
        listOf("https://api.open-meteo.com/v1/forecast?", "latitude=55.7522", "longitude=37.6156", "timeformat=unixtime", "timezone=auto", "wind_speed_unit=ms", "precipitation_sum")
            .forEach { assertTrue(it in url, "$it missing from $url") }
    }

    @Test fun aForecastIsReadInTheCitysTimeWithEachHoursOwnPrecipitation() {
        val forecast = Source.OPEN_METEO.forecast(Fixtures.openMeteo(), Fixtures.NOW, Fixtures.MOSCOW.zone)
        assertEquals(Source.OPEN_METEO, forecast.source)
        val now = forecast.current(Fixtures.NOW)!!
        assertEquals(Forecast.Hour(Instant.parse("2026-11-09T11:00:00Z"), LocalDateTime.of(2026, 11, 9, 14, 0), -3.4, 5.0,
            Symbol.ofWmo(3, true), 0.2, 1013.2, 4.4, 315.0), now)
        assertEquals(LocalDateTime.of(2026, 11, 9, 3, 0), forecast.hours.first().time)
        assertNull(forecast.hours.last().precipitationMm)
        assertEquals(listOf(15, 18, 21), forecast.hoursAfter(Fixtures.NOW, 3, 3).map { it.time.hour })
    }

    @Test fun daysKeepTheirDatesAcrossAClockChange() {
        val offset = 2 * 3600
        val midnights = (24..27).map { LocalDate.of(2026, 10, it).atStartOfDay().toEpochSecond(ZoneOffset.ofTotalSeconds(offset)) }
        val body = JSONObject()
            .put("utc_offset_seconds", offset)
            .put("hourly", JSONObject().put("time", JSONArray()).put("temperature_2m", JSONArray()).put("weather_code", JSONArray()))
            .put("daily", JSONObject()
                .put("time", JSONArray(midnights))
                .put("weather_code", JSONArray(midnights.map { 3 }))
                .put("temperature_2m_max", JSONArray(midnights.map { 10.0 }))
                .put("temperature_2m_min", JSONArray(midnights.map { 5.0 })))
            .toString()
        val days = Source.OPEN_METEO.forecast(body, Fixtures.NOW, ZoneId.of("Europe/Berlin")).days
        assertEquals((24..27).map { LocalDate.of(2026, 10, it) }, days.map { it.date })
    }

    @Test fun coordinatesAreShortAndNeverInScientificNotation() {
        val url = OpenMeteo.forecastUrl(Fixtures.MOSCOW.copy(latitude = 51.47783, longitude = 0.0004))
        assertTrue("latitude=51.4778&longitude=0.0004&" in url, url)
    }

    @Test fun daysFollowTheCitysMidnight() {
        val days = Source.OPEN_METEO.forecast(Fixtures.openMeteo(), Fixtures.NOW, Fixtures.MOSCOW.zone).days
        assertEquals(LocalDate.of(2026, 11, 9), days.first().date)
        assertEquals(Forecast.Day(LocalDate.of(2026, 11, 10), Symbol.ofWmo(61, true), 3.0, -5.0, 1.5), days[1])
    }

    @Test fun aPlaceSavedWithoutATimeZoneUsesThePhones() {
        assertEquals(ZoneId.systemDefault(), Fixtures.MOSCOW.copy(timezone = "").zone)
        assertEquals(ZoneId.of("Europe/Moscow"), Place.fromJson(Fixtures.MOSCOW.toJson()).zone)
    }
}
