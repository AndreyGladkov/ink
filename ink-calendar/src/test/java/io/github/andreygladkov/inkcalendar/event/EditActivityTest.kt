package io.github.andreygladkov.inkcalendar.event

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkDateActivity
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkTimeActivity
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class EditActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        Fixtures.fixNow()
        Fixtures.allow(application)
        provider = FakeCalendarProvider.install()
        provider.calendar(1, "Личный")
        provider.calendar(2, "Работа", account = "work@example.org")
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun aNewEventStartsAtTheNextHourOfTheChosenDayAndLastsAnHour() {
        val activity = create(TODAY.plusDays(1))
        assertTrue(texts(activity).containsAll(listOf("Новое событие", "Пт, 27 нояб.", "13:00", "14:00", "Личный", "Работа")))
        assertTrue(button(activity, "Личный").isSelected)
        contentRoot(activity).find<InkEditText>().single().setText("Врач")
        button(activity, "Работа").performClick()
        button(activity, "1 час").performClick()
        button(activity, "Готово").performClick()
        val values = provider.events.values.single()
        assertEquals(listOf<Any?>("Врач", 2L, at(TODAY.plusDays(1), 13), at(TODAY.plusDays(1), 14)),
            listOf(Events.TITLE, Events.CALENDAR_ID, Events.DTSTART, Events.DTEND).map(values::get))
        assertEquals(listOf(60), provider.reminders.map { it[Reminders.MINUTES] })
        assertEquals(at(TODAY.plusDays(1), 13), EditActivity.savedBegin(shadowOf(activity).resultIntent))
        assertTrue(activity.isFinishing)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun movingTheStartKeepsTheLengthAndAnEndBeforeTheStartIsNotSaved() {
        val activity = create(TODAY)
        pick(activity, "13:00", InkTimeActivity::class.java, Intent().putExtra("time", LocalTime.of(9, 30).toSecondOfDay()))
        assertTrue(texts(activity).containsAll(listOf("9:30", "10:30")))
        pick(activity, "10:30", InkTimeActivity::class.java, Intent().putExtra("time", LocalTime.of(8, 0).toSecondOfDay()))
        button(activity, "Готово").performClick()
        assertTrue(texts(activity).contains("Событие кончается раньше, чем начинается"))
        assertTrue(provider.events.isEmpty())
        assertFalse(activity.isFinishing)
    }

    @Test fun anAllDayEventHidesTheTimesAndTakesItsDates() {
        val activity = create(TODAY)
        button(activity, "Вкл").performClick()
        assertTrue(contentRoot(activity).find<InkButton>().none { it.text.toString() == "13:00" && it.visibility == View.VISIBLE })
        pick(activity, "Чт, 26 нояб.", InkDateActivity::class.java, Intent().putExtra("date", TODAY.plusDays(1).toEpochDay()), index = 1)
        button(activity, "Готово").performClick()
        val values = provider.events.values.single()
        assertEquals(listOf<Any?>(1, utcDay(TODAY), utcDay(TODAY.plusDays(2))), listOf(Events.ALL_DAY, Events.DTSTART, Events.DTEND).map(values::get))
    }

    @Test fun withNowhereToWriteALocalCalendarIsMade() {
        provider.calendars.clear()
        provider.calendar(3, "Праздники", accessLevel = android.provider.CalendarContract.Calendars.CAL_ACCESS_READ)
        val activity = create(TODAY)
        button(activity, "Готово").performClick()
        val local = provider.calendars.single { it[android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME] == "Телефон" }
        assertEquals(local[android.provider.CalendarContract.Calendars._ID], provider.events.values.single().get(Events.CALENDAR_ID))
    }

    @Test fun anEventIsEditedInPlaceKeepingRemindersItDidNotShow() {
        provider.instance(7, at(TODAY, 9), at(TODAY, 10), "Стендап", calendarId = 2)
        provider.reminder(7, 10)
        provider.reminder(7, 1440)
        val activity = Robolectric.buildActivity(EditActivity::class.java, EditActivity.edit(application, 7, at(TODAY, 9))).setup().get()
        assertEquals("Стендап", contentRoot(activity).find<InkEditText>().single().text.toString())
        assertTrue(button(activity, "10 мин").isSelected)
        assertTrue(texts(activity).none { it == "Личный" })
        contentRoot(activity).find<InkEditText>().single().setText("Планёрка")
        button(activity, "Готово").performClick()
        assertEquals("Планёрка", provider.events.getValue(7).getAsString(Events.TITLE))
        assertEquals(listOf(10, 1440), provider.reminders.map { it[Reminders.MINUTES] })
    }

    private fun create(date: java.time.LocalDate): Activity =
        Robolectric.buildActivity(EditActivity::class.java, EditActivity.create(application, date)).setup().get()

    private fun pick(activity: Activity, label: String, picker: Class<*>, result: Intent, index: Int = 0) {
        contentRoot(activity).find<InkButton>().filter { it.text.toString() == label }[index].performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(picker.name, asked.intent.component?.className)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, result)
    }

    private fun button(activity: Activity, label: String) = contentRoot(activity).find<InkButton>().single { it.text.toString() == label }

    private fun texts(activity: Activity) = contentRoot(activity).find<InkTextView>().filter { it.visibility == View.VISIBLE }.map { it.text.toString() }
}
