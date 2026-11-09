package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.R
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MetNoTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    @Test fun coordinatesAreCutToFourDecimals() {
        assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=55.7522&lon=37.6156", MetNo.forecastUrl(Fixtures.MOSCOW))
        assertEquals("https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=-33.8679&lon=151.2073",
            MetNo.forecastUrl(Fixtures.MOSCOW.copy(latitude = -33.86785, longitude = 151.20732)))
    }

    @Test fun hoursAreReadInTheCitysTime() {
        val forecast = MetNo.forecast(Fixtures.forecast(), Fixtures.NOW, moscow)
        assertEquals(Fixtures.NOW, forecast.fetchedAt)
        val now = forecast.hours.single { it.time == LocalDateTime.of(2026, 11, 9, 14, 0) }
        assertEquals(Forecast.Hour(Instant.parse("2026-11-09T11:00:00Z"), LocalDateTime.of(2026, 11, 9, 14, 0), -3.4, -7.6, Symbol.of("cloudy"), 0.4, 1013.2, 4.4, 315.0), now)
        assertEquals(LocalDateTime.of(2026, 11, 9, 3, 0), forecast.hours.first().time)
    }

    @Test fun anHourlyStepTakesTheNextHoursPrecipitationAndASixHourlyStepItsSixHours() {
        val series = listOf(
            Fixtures.step(Instant.parse("2026-11-11T16:00:00Z"), 1.0, "rain", hourMm = 0.3, span = Fixtures.Span("rain", 2.0, 0.0, 1.8)),
            Fixtures.step(Instant.parse("2026-11-11T17:00:00Z"), 1.0, "rain", hourMm = 0.5, span = Fixtures.Span("rain", 2.0, 0.0, 2.5)),
            Fixtures.step(Instant.parse("2026-11-11T23:00:00Z"), 1.0, "rain", span = Fixtures.Span("rain", 2.0, 0.0, 4.0)),
        )
        val hours = MetNo.forecast(Fixtures.metNo(series), Fixtures.NOW, ZoneOffset.UTC).hours
        assertEquals(listOf(0.3, 2.5, 4.0), hours.map { it.precipitationMm })
    }

    @Test fun aClockChangeDoesNotTurnAnHourlyStepIntoASixHourOne() {
        val series = (0 until 4).map { Fixtures.step(Instant.parse("2026-03-29T00:00:00Z").plusSeconds(it * 3600L), 1.0, "rain", hourMm = 0.5, span = Fixtures.Span("rain", 2.0, 0.0, 3.0)) }
        val hours = MetNo.forecast(Fixtures.metNo(series), Fixtures.NOW, ZoneId.of("Europe/Paris")).hours
        assertEquals(listOf(1, 3, 4, 5), hours.map { it.time.hour })
        assertEquals(listOf(0.5, 0.5, 0.5, 0.5), hours.map { it.precipitationMm })
    }

    @Test fun aDayTakesItsExtremesFromSpansWhollyInsideItAndItsWeatherFromNoon() {
        val series = listOf(
            Fixtures.step(Instant.parse("2026-11-09T21:00:00Z"), 1.0, "cloudy", hourMm = 0.0, span = Fixtures.Span("cloudy", 30.0, -30.0, 0.0)),
            Fixtures.step(Instant.parse("2026-11-09T22:00:00Z"), 1.0, "cloudy", span = Fixtures.Span("cloudy", 2.0, 0.0, 0.0)),
            Fixtures.step(Instant.parse("2026-11-10T04:00:00Z"), 5.0, "cloudy", span = Fixtures.Span("rain", 8.0, 4.0, 1.0)),
            Fixtures.step(Instant.parse("2026-11-10T10:00:00Z"), 7.0, "cloudy", span = Fixtures.Span("snow_night", 9.0, 6.0, 2.0)),
            Fixtures.step(Instant.parse("2026-11-10T16:00:00Z"), 3.0, "cloudy", span = Fixtures.Span("fog", 4.0, -2.0, 0.5)),
            Fixtures.step(Instant.parse("2026-11-10T22:00:00Z"), 0.0, "cloudy", span = Fixtures.Span("fog", 1.0, -1.0, 0.0)),
        )
        val days = MetNo.forecast(Fixtures.metNo(series), Fixtures.NOW, ZoneOffset.UTC).days
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10)), days.map { it.date })
        assertEquals(1.0 to 1.0, days[0].max to days[0].min)
        val tuesday = days[1]
        assertEquals(9.0 to -2.0, tuesday.max to tuesday.min)
        assertEquals(Symbol(Sky.SNOW, isDay = true, R.string.snow), tuesday.symbol)
        assertEquals(3.5, tuesday.precipitationMm)
    }

    @Test fun aWetDayShowsItsWettestSixHoursAndADryOneItsNoon() {
        fun day(evening: Double) = MetNo.forecast(Fixtures.metNo(listOf(
            Fixtures.step(Instant.parse("2026-11-10T03:00:00Z"), 1.0, "cloudy", span = Fixtures.Span("lightsleet", 2.0, 0.0, 0.2)),
            Fixtures.step(Instant.parse("2026-11-10T09:00:00Z"), 1.0, "cloudy", span = Fixtures.Span("cloudy", 2.0, 0.0, 0.0)),
            Fixtures.step(Instant.parse("2026-11-10T15:00:00Z"), 1.0, "cloudy", span = Fixtures.Span("rain", 2.0, 0.0, evening)),
            Fixtures.step(Instant.parse("2026-11-10T21:00:00Z"), 1.0, "cloudy", span = Fixtures.Span("cloudy", 2.0, 0.0, 0.0)),
        )), Fixtures.NOW, ZoneOffset.UTC).days.single()
        assertEquals(R.string.rain, day(evening = 2.5).symbol.description)
        assertEquals(R.string.weather_cloudy, day(evening = 0.5).symbol.description)
    }

    @Test fun stepsWithoutATemperatureOrASymbolAreLeftOut() {
        assertEquals(30, MetNo.forecast(Fixtures.forecast(temperatures = false), Fixtures.NOW, moscow).hours.size)
        val bare = JSONObject(Fixtures.metNo(listOf(Fixtures.step(Fixtures.NOW, 1.0, "cloudy")))).apply {
            getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data").remove("next_12_hours")
        }.toString()
        assertTrue(MetNo.forecast(bare, Fixtures.NOW, moscow).hours.isEmpty())
    }

    @Test fun missingOptionalValuesStayEmpty() {
        val series = listOf(Fixtures.step(Fixtures.NOW, 1.0, "cloudy", hourMm = 0.0))
        val body = JSONObject(Fixtures.metNo(series)).apply {
            getJSONObject("properties").getJSONArray("timeseries").getJSONObject(0).getJSONObject("data").getJSONObject("instant").getJSONObject("details")
                .put("air_pressure_at_sea_level", JSONObject.NULL).remove("wind_from_direction")
        }.toString()
        val hour = MetNo.forecast(body, Fixtures.NOW, moscow).hours.single()
        assertNull(hour.pressureHpa)
        assertNull(hour.windDegrees)
        assertNull(hour.feelsLike)
    }

    @Test fun aBodyWithoutAForecastIsRejected() {
        assertFailsWith<Exception> { MetNo.forecast("""{"type":"Feature","geometry":{}}""", Fixtures.NOW, moscow) }
    }
}
