package io.github.andreygladkov.inkclock.alarm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class AlarmLabelsTest {
    private val labels = AlarmLabels(ApplicationProvider.getApplicationContext<Application>())
    private fun at(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), ZoneId.of("Asia/Almaty"))

    @Test fun daysAreSummarisedInWordsOrListedFromMonday() {
        assertEquals("Один раз", labels.days(emptySet()))
        assertEquals("Каждый день", labels.days(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)))
        assertEquals("По будням", labels.days(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)))
        assertEquals("По выходным", labels.days(setOf(SATURDAY, SUNDAY)))
        assertEquals("Пн, Ср, Вс", labels.days(setOf(SUNDAY, WEDNESDAY, MONDAY)))
    }

    @Test fun theSummaryAddsTheLabel() {
        assertEquals("Один раз · Работа", labels.summary(Alarm(1, 7, 0, label = "Работа")))
    }

    @Test fun timeUntilRoundsUpToTheMinute() {
        val now = at("2026-11-05T22:44:30")
        assertEquals("1 мин", labels.until(now, at("2026-11-05T22:45")))
        assertEquals("1 ч", labels.until(now, at("2026-11-05T23:44:30")))
        assertEquals("1 ч 1 мин", labels.until(now, at("2026-11-05T23:45")))
        assertEquals("8 ч 16 мин", labels.until(now, at("2026-11-06T07:00")))
    }

    @Test fun theNextAlarmNamesItsDay() {
        val now = at("2026-11-05T22:44:30")
        assertEquals("Следующий: завтра 07:00, через 8 ч 16 мин", labels.next(NextAlarm(Alarm(1, 7, 0), at("2026-11-06T07:00")), now))
        assertEquals("Следующий: сегодня 23:00, через 16 мин", labels.next(NextAlarm(Alarm(1, 23, 0), at("2026-11-05T23:00")), now))
        assertEquals("Следующий: Пн 07:00, через 3 дн", labels.next(NextAlarm(Alarm(1, 7, 0), at("2026-11-09T07:00")), now).replace(Regex("через .*"), "через 3 дн"))
    }
}
