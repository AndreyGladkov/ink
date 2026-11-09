package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.Fixtures
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ForecastTest {
    private val forecast = Source.MET_NO.forecast(Fixtures.forecast(), Fixtures.NOW, Fixtures.MOSCOW.zone)

    @Test fun hoursStartAtTheNextFullHourInTheCitysTimeAndStepByThree() {
        assertEquals(listOf(15, 18, 21, 0, 3, 6, 9, 12), forecast.hoursAfter(Fixtures.NOW, 8, 3).map { it.time.hour })
        assertEquals(LocalDate.of(2026, 11, 10), forecast.hoursAfter(Fixtures.NOW, 8, 3)[3].time.toLocalDate())
    }

    @Test fun theSameMomentGivesOtherHoursInAnotherTimeZone() {
        val paris = Source.MET_NO.forecast(Fixtures.forecast(), Fixtures.NOW, Fixtures.PARIS.zone)
        assertEquals(listOf(13, 16), paris.hoursAfter(Fixtures.NOW, 2, 3).map { it.time.hour })
    }

    @Test fun aZoneHalfAnHourOffStillHasItsHours() {
        val kolkata = Source.MET_NO.forecast(Fixtures.forecast(), Fixtures.NOW, ZoneId.of("Asia/Kolkata"))
        assertEquals(listOf("17:30", "20:30", "23:30"), kolkata.hoursAfter(Fixtures.NOW, 3, 3).map { "${it.time.hour}:${it.time.minute}" })
    }

    @Test fun hoursRepeatedWhenTheClocksGoBackAreBothKept() {
        val paris = Source.MET_NO.forecast(Fixtures.metNo((0 until 6).map {
            Fixtures.step(Instant.parse("2026-10-25T00:00:00Z").plusSeconds(it * 3600L), it.toDouble(), "cloudy", hourMm = 0.0)
        }), Fixtures.NOW, ZoneId.of("Europe/Paris"))
        val hours = paris.hoursAfter(Instant.parse("2026-10-24T23:30:00Z"), 6, 1)
        assertEquals(listOf(2, 2, 3, 4, 5, 6), hours.map { it.time.hour })
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0), hours.map { it.temperature })
    }

    @Test fun aStepAddsUpThePrecipitationOfItsHours() {
        assertEquals(listOf(0.6, 0.2), forecast.hoursAfter(Fixtures.NOW, 2, 3).map { it.precipitationMm!!.let { mm -> Math.round(mm * 10) / 10.0 } })
    }

    @Test fun theCurrentHourIsTheOneUnderWay() {
        assertEquals(-3.4, forecast.current(Fixtures.NOW)!!.temperature)
        assertEquals(LocalDateTime.of(2026, 11, 9, 3, 0), forecast.current(Instant.parse("2026-11-08T12:00:00Z"))!!.time)
        assertNull(forecast.current(Instant.parse("2026-11-30T12:00:00Z")))
    }

    @Test fun hoursPastTheHourlyPartLineUpWithSixHourSteps() {
        val later = forecast.hoursAfter(Fixtures.NOW.plus(Duration.ofDays(4)), 8, 3).map { it.time.hour }
        assertTrue(later.size > 1 && later.zipWithNext { a, b -> (b - a + 24) % 24 }.all { it == 6 }, "$later")
    }

    @Test fun daysStartToday() {
        assertEquals(LocalDate.of(2026, 11, 9), forecast.daysFrom(Fixtures.NOW, 7).first().date)
        assertEquals(LocalDate.of(2026, 11, 11), forecast.daysFrom(Fixtures.NOW.plus(Duration.ofDays(2)), 7).first().date)
        assertEquals(7, forecast.daysFrom(Fixtures.NOW.plus(Duration.ofDays(2)), 7).size)
    }

    @Test fun theAgeCountsFromTheFetch() {
        assertEquals(Duration.ofHours(4), forecast.age(Fixtures.NOW.plus(Duration.ofHours(4))))
    }
}
