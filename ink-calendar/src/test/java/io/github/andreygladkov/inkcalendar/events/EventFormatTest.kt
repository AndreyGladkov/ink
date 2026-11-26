package io.github.andreygladkov.inkcalendar.events

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class EventFormatTest {
    private val format = EventFormat(ApplicationProvider.getApplicationContext<Context>(), ZONE)
    private val day = LocalDate.of(2026, 11, 26)

    @Test fun aDayIsNamedInFull() {
        assertEquals("Четверг, 26 ноября", format.day(day))
        assertEquals("Четверг", format.weekday(day))
        assertEquals("Ноябрь", format.month(day))
    }

    @Test fun theTimeOnADaySaysHowMuchOfItTheEventTakes() {
        val night = occurrence(at(day, 23), at(day.plusDays(2), 2))
        assertEquals("9:00–10:30", format.timeOn(occurrence(at(day, 9), at(day, 10, 30)), day))
        assertEquals("9:00", format.timeOn(occurrence(at(day, 9), at(day, 9)), day))
        assertEquals("с 23:00", format.timeOn(night, day))
        assertEquals("Весь день", format.timeOn(night, day.plusDays(1)))
        assertEquals("до 2:00", format.timeOn(night, day.plusDays(2)))
        assertEquals("Весь день", format.timeOn(occurrence(utcDay(day), utcDay(day.plusDays(1)), allDay = true), day))
        assertEquals("22:00–0:00", format.timeOn(occurrence(at(day, 22), at(day.plusDays(1), 0)), day))
    }

    @Test fun aWidgetLineStartsWithTheStartOrWithHowTheEventRunsIntoTheDay() {
        val night = occurrence(at(day, 23), at(day.plusDays(2), 2))
        assertEquals("23:00", format.startOn(night, day))
        assertEquals(null, format.startOn(night, day.plusDays(1)))
        assertEquals("до 2:00", format.startOn(night, day.plusDays(2)))
    }

    @Test fun theCardSaysWhenTheEventIs() {
        assertEquals(listOf("Четверг, 26 ноября", "9:00–10:30"), format.span(occurrence(at(day, 9), at(day, 10, 30))))
        assertEquals(listOf("Четверг, 26 ноября"), format.span(occurrence(utcDay(day), utcDay(day.plusDays(1)), allDay = true)))
        assertEquals(listOf("26 ноября – 28 ноября"), format.span(occurrence(utcDay(day), utcDay(day.plusDays(3)), allDay = true)))
        assertEquals(listOf("26 ноября, 23:00 – 28 ноября, 2:00"), format.span(occurrence(at(day, 23), at(day.plusDays(2), 2))))
    }

    @Test fun remindersAreSaidInTheLargestWholeUnit() {
        assertEquals("В момент начала", format.reminder(0))
        assertEquals("За 10 минут", format.reminder(10))
        assertEquals("За 1 час", format.reminder(60))
        assertEquals("За 2 часа", format.reminder(120))
        assertEquals("За 1 день", format.reminder(1440))
        assertEquals("За 90 минут", format.reminder(90))
    }

    private fun occurrence(begin: Long, end: Long, allDay: Boolean = false) = Occurrence(1, begin, end, allDay, "", 1, "", "", "", repeats = false)
}
