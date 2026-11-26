package io.github.andreygladkov.inkcalendar.events

import android.content.Context
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.ZONE
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class EventWriterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val writer = EventWriter(context.contentResolver, ZONE)
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        provider = FakeCalendarProvider.install()
    }

    @Test fun aTimedEventIsWrittenInThePhonesZoneWithItsReminder() {
        val id = writer.insert(EventDraft("Врач", false, TODAY.atTime(14, 0), TODAY.atTime(15, 0), 1, 60))!!
        val values = provider.events.getValue(id)
        assertEquals(listOf<Any?>(1L, "Врач", 0, 1, at(TODAY, 14), at(TODAY, 15), "Europe/Moscow"),
            listOf(Events.CALENDAR_ID, Events.TITLE, Events.ALL_DAY, Events.HAS_ALARM, Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE).map(values::get))
        assertEquals(listOf(Triple(id, 60, Reminders.METHOD_ALERT)),
            provider.reminders.map { Triple(it[Reminders.EVENT_ID], it[Reminders.MINUTES], it[Reminders.METHOD]) })
    }

    @Test fun anAllDayEventIsWrittenAtMidnightUtcWithItsEndAfterTheLastDay() {
        val id = writer.insert(EventDraft("Поездка", true, TODAY.atStartOfDay(), TODAY.plusDays(2).atStartOfDay(), 1, null))!!
        val values = provider.events.getValue(id)
        assertEquals(listOf<Any?>(1, 0, utcDay(TODAY), utcDay(TODAY.plusDays(3)), "UTC"),
            listOf(Events.ALL_DAY, Events.HAS_ALARM, Events.DTSTART, Events.DTEND, Events.EVENT_TIMEZONE).map(values::get))
        assertEquals(emptyList(), provider.reminders)
    }

    @Test fun remindersAreReplacedOnlyWhenTheFormChangedThem() {
        provider.reminder(7, 10)
        provider.reminder(7, 1440)
        val draft = EventDraft("Врач", false, TODAY.atTime(14, 0), TODAY.atTime(15, 0), 1, 10)
        writer.update(7, draft, reminderChanged = false)
        assertEquals(listOf(10, 1440), provider.reminders.map { it[Reminders.MINUTES] })
        assertEquals("Врач", provider.events.getValue(7).getAsString(Events.TITLE))
        writer.update(7, draft.copy(reminder = 60), reminderChanged = true)
        assertEquals(listOf(60), provider.reminders.map { it[Reminders.MINUTES] })
        writer.delete(7)
        assertEquals(listOf(7L), provider.deletedEvents)
    }

    @Test fun aLocalCalendarIsMadeAsItsOwnSyncAdapterAndBecomesTheDefault() {
        writer.createLocalCalendar("Телефон")
        val calendars = EventStore.of(context).writableCalendars()
        assertEquals(listOf("Телефон"), calendars.map { it.name })
        assertEquals(calendars.single(), EventWriter.defaultCalendar(context, calendars))
    }

    @Test fun theDefaultCalendarIsTheLastUsedThenOneNotHidden() {
        val calendars = listOf(CalendarInfo(1, "Личный", "a", true), CalendarInfo(2, "Работа", "b", true))
        EventStore.setShown(context, 1, false)
        assertEquals(2L, EventWriter.defaultCalendar(context, calendars)?.id)
        EventWriter.setDefaultCalendar(context, 1)
        assertEquals(1L, EventWriter.defaultCalendar(context, calendars)?.id)
    }
}
