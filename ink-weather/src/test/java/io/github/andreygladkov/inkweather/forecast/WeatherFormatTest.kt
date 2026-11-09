package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class WeatherFormatTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val moscow = ZoneOffset.ofHours(3)
    private val format = WeatherFormat(context, fahrenheit = false, zone = moscow)

    @Test fun temperaturesAreWholeDegreesWithATypographicMinus() {
        assertEquals("−3°", format.temperature(-3.4))
        assertEquals("0°", format.temperature(-0.4))
        assertEquals("12°", format.temperature(11.5))
        assertEquals("5° / −1°", format.range(5.0, -1.0))
    }

    @Test fun fahrenheitIsConvertedFromCelsius() {
        val us = WeatherFormat(context, fahrenheit = true, zone = moscow)
        assertEquals("32°", us.temperature(0.0))
        assertEquals("−4°", us.temperature(-20.0))
    }

    @Test fun russianPressureIsInMillimetresOfMercury() {
        assertEquals("Давление 760 мм рт. ст.", format.pressure(1013.25))
    }

    @Config(qualifiers = "en")
    @Test fun otherPressureIsInHectopascals() {
        assertEquals("Pressure 1013 hPa", WeatherFormat(context, fahrenheit = false, zone = moscow).pressure(1013.25))
    }

    @Test fun windNamesTheNearestOfEightDirections() {
        assertEquals("Ветер 4 м/с, СЗ", format.wind(4.4, 315.0))
        assertEquals("Ветер 3 м/с, С", format.wind(3.0, 350.0))
        assertEquals("Ветер 3 м/с, В", format.wind(3.0, 100.0))
        assertEquals("Ветер 0 м/с", format.wind(0.2, null))
    }

    @Test fun precipitationThatRoundsToNothingSaysNothing() {
        assertNull(format.amount(0.04))
        assertNull(format.amount(null))
        assertEquals("0,1 мм", format.amount(0.05))
        assertEquals("12,5 мм", format.amount(12.46))
        assertNull(format.precipitation(0.0))
        assertEquals("Осадки 0,4 мм", format.precipitation(0.4))
    }

    @Test fun thunderIsAddedToTheWeather() {
        assertEquals("Небольшой ливень", format.description(Symbol.of("lightrainshowers_day")))
        assertEquals("Снегопад, гроза", format.description(Symbol.of("snowshowersandthunder_night")))
    }

    @Test fun daysAreNamedFromToday() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Сегодня", format.day(today, today))
        assertEquals("Вт 10", format.day(today.plusDays(1), today))
        assertEquals("Ср", format.shortDay(today.plusDays(2)))
    }

    @Test fun hoursAreShownWithoutMinutes() {
        assertEquals("15", format.hour(LocalDateTime.of(2026, 11, 9, 15, 0)))
    }

    @Test fun anUpdateTodayGivesTheTimeAndAnOlderOneTheDate() {
        assertEquals("Обновлено в 14:20", format.updated(Fixtures.NOW, Fixtures.NOW.plus(Duration.ofHours(4))))
        assertEquals("14:20", format.updatedShort(Fixtures.NOW, Fixtures.NOW.plus(Duration.ofHours(4))))
        assertEquals("Обновлено 9 нояб., 14:20", format.updated(Fixtures.NOW, Fixtures.NOW.plus(Duration.ofDays(1))))
        assertEquals("9 нояб.", format.updatedShort(Fixtures.NOW, Fixtures.NOW.plus(Duration.ofDays(1))))
    }
}
