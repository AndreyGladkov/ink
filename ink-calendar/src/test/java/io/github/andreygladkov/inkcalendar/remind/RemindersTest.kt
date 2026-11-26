package io.github.andreygladkov.inkcalendar.remind

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.events.EventStore
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import io.github.andreygladkov.ink.A9_QUALIFIERS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class RemindersTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = application.getSystemService(NotificationManager::class.java)
    private val alarmTime = at(TODAY, 13)
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        Fixtures.fixNow()
        Fixtures.allow(application)
        provider = FakeCalendarProvider.install()
        provider.calendar(1, "Личный")
        provider.calendar(2, "Работа")
        provider.alert(alarmTime, 5, at(TODAY, 14), at(TODAY, 15), "Врач", location = "Поликлиника №2")
        provider.alert(alarmTime, 6, at(TODAY, 14), at(TODAY, 16), "Совещание", calendarId = 2)
        provider.alert(at(TODAY, 20), 7, at(TODAY, 21), at(TODAY, 22), "Позже")
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun theProvidersReminderShowsItsEventsWithWhenAndWhere() {
        remind()
        val shown = shadowOf(notifications).allNotifications.map { shadowOf(it) }
        assertEquals(listOf("Врач", "Совещание"), shown.map { it.contentTitle.toString() }.sorted())
        val doctor = shadowOf(notifications).allNotifications.single { shadowOf(it).contentTitle == "Врач" }
        assertEquals("Четверг, 26 ноября, 14:00–15:00, Поликлиника №2", shadowOf(doctor).contentText)
        assertEquals(listOf("Через 10 минут", "Закрыть"), doctor.actions.map { it.title.toString() })
    }

    @Test fun nothingIsShownForAHiddenCalendarOrWithRemindersOff() {
        EventStore.setShown(application, 2, false)
        remind()
        assertEquals(listOf("Врач"), shadowOf(notifications).allNotifications.map { shadowOf(it).contentTitle.toString() })
        notifications.cancelAll()
        Reminders.setOn(application, false)
        remind()
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test fun snoozingTakesTheReminderAwayAndBringsItBackInTenMinutes() {
        remind()
        val doctor = shadowOf(notifications).allNotifications.single { shadowOf(it).contentTitle == "Врач" }
        shadowOf(doctor.actions.first().actionIntent).savedIntent.let { ReminderActions().onReceive(application, it) }
        assertEquals(listOf("Совещание"), shadowOf(notifications).allNotifications.map { shadowOf(it).contentTitle.toString() })
        val alarm = shadowOf(application.getSystemService(AlarmManager::class.java)).scheduledAlarms.single()
        assertEquals(at(TODAY, 12, 10), alarm.triggerAtTime)
        ReminderActions().onReceive(application, shadowOf(alarm.operation).savedIntent)
        assertEquals(listOf("Врач", "Совещание"), shadowOf(notifications).allNotifications.map { shadowOf(it).contentTitle.toString() }.sorted())
        val again = shadowOf(notifications).allNotifications.single { shadowOf(it).contentTitle == "Врач" }
        shadowOf(again.actions.last().actionIntent).savedIntent.let { ReminderActions().onReceive(application, it) }
        assertEquals(listOf("Совещание"), shadowOf(notifications).allNotifications.map { shadowOf(it).contentTitle.toString() })
    }

    private fun remind() = EventReminderReceiver().onReceive(application,
        Intent(CalendarContract.ACTION_EVENT_REMINDER, Uri.parse("content://com.android.calendar/$alarmTime")))
}
