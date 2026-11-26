package io.github.andreygladkov.inkcalendar.events

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class EventStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val day = LocalDate.of(2026, 11, 26)
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        provider = FakeCalendarProvider.install()
        provider.calendar(1, "Личный")
        provider.calendar(2, "Работа", account = "work@example.org", accessLevel = CalendarContract.Calendars.CAL_ACCESS_READ)
        provider.calendar(3, "Скрытый телефоном", visible = false)
        provider.instance(1, at(day, 9), at(day, 10), "Стендап", calendarId = 2, location = "Офис", rrule = "FREQ=DAILY")
        provider.instance(2, utcDay(day.plusDays(1)), utcDay(day.plusDays(2)), "День рождения", allDay = true)
        provider.instance(3, at(day, 12), at(day, 13), "Невидимое", calendarId = 3)
        provider.instance(4, at(day.plusDays(5), 12), at(day.plusDays(5), 13), "Потом")
    }

    @Test fun calendarsThePhoneShowsAreListedByAccountWithWhetherTheyTakeEvents() {
        assertEquals(listOf(CalendarInfo(1, "Личный", "me@example.org", true), CalendarInfo(2, "Работа", "work@example.org", false)),
            EventStore.of(context).calendars())
    }

    @Test fun occurrencesInTheRangeLeaveOutHiddenCalendars() {
        assertEquals(listOf("Стендап", "День рождения"), EventStore.of(context).occurrences(day, day.plusDays(2), ZONE).map { it.title })
        EventStore.setShown(context, 2, false)
        assertEquals(listOf("День рождения"), EventStore.of(context).occurrences(day, day.plusDays(2), ZONE).map { it.title })
        EventStore.setShown(context, 2, true)
        assertEquals(emptySet(), EventStore.hidden(context))
    }

    @Test fun anOccurrenceIsFoundByItsEventAndStartWithEverythingTheCardShows() {
        val standup = EventStore.of(context).occurrence(1, at(day, 9), ZONE)!!
        assertEquals(listOf("Стендап", "Работа", "Офис"), listOf(standup.title, standup.calendarName, standup.location))
        assertEquals(true, standup.repeats)
        assertNull(EventStore.of(context).occurrence(1, at(day, 10), ZONE))
    }

    @Test fun onlyRemindersThatAlertAreKept() {
        provider.reminder(1, 60)
        provider.reminder(1, 10)
        provider.reminder(1, 30, CalendarContract.Reminders.METHOD_EMAIL)
        assertEquals(listOf(10, 60), EventStore.of(context).reminders(1))
    }
}
