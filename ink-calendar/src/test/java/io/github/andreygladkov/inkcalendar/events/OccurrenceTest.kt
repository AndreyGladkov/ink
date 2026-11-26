package io.github.andreygladkov.inkcalendar.events

import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OccurrenceTest {
    private val day = LocalDate.of(2026, 11, 26)

    @Test fun anAllDayEventKeepsItsDatesWhateverTheZone() {
        val trip = occurrence(utcDay(day), utcDay(day.plusDays(3)), allDay = true)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), trip.days(ZONE).toList())
        assertTrue(trip.takesWholeDay(day.plusDays(1), ZONE))
    }

    @Test fun anEventEndingAtMidnightBelongsToTheDayBefore() {
        val evening = occurrence(at(day, 22), at(day.plusDays(1), 0))
        assertEquals(listOf(day), evening.days(ZONE).toList())
        assertFalse(evening.occursOn(day.plusDays(1), ZONE))
    }

    @Test fun aNightEventIsOnBothDaysAndAMomentOnItsOwn() {
        assertEquals(listOf(day, day.plusDays(1)), occurrence(at(day, 23), at(day.plusDays(1), 2)).days(ZONE).toList())
        assertEquals(listOf(day), occurrence(at(day, 9), at(day, 9)).days(ZONE).toList())
    }

    @Test fun aDayListsWholeDayEventsFirstThenByTime() {
        val dinner = occurrence(at(day, 19), at(day, 20), title = "Ужин")
        val standup = occurrence(at(day, 9), at(day, 10), title = "Стендап")
        val birthday = occurrence(utcDay(day), utcDay(day.plusDays(1)), allDay = true, title = "День рождения")
        val tomorrow = occurrence(at(day.plusDays(1), 9), at(day.plusDays(1), 10), title = "Завтра")
        assertEquals(listOf("День рождения", "Стендап", "Ужин"), Occurrence.onDay(listOf(dinner, tomorrow, standup, birthday), day, ZONE).map { it.title })
    }

    private fun occurrence(begin: Long, end: Long, allDay: Boolean = false, title: String = "") =
        Occurrence(1, begin, end, allDay, title, 1, "", "", "", repeats = false)
}
