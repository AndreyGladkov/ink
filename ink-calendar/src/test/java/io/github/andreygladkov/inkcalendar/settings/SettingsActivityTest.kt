package io.github.andreygladkov.inkcalendar.settings

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures
import io.github.andreygladkov.inkcalendar.events.EventStore
import io.github.andreygladkov.inkcalendar.events.EventWriter
import io.github.andreygladkov.inkcalendar.remind.Reminders
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test fun eachCalendarCanBeHidden() {
        Fixtures.allow(application)
        FakeCalendarProvider.install().apply {
            calendar(1, "Личный")
            calendar(2, "Работа", account = "work@example.org")
        }
        val activity = open()
        assertTrue(texts(activity).containsAll(listOf("Календари", "Личный", "Работа")))
        val work = contentRoot(activity).firstOnPages<InkChoiceRow<*>> { row -> row.findOnPagesTitle() == "Работа" }
        work.findButton("Выкл").performClick()
        assertEquals(setOf(2L), EventStore.hidden(application))
    }

    @Test fun newEventsGoToTheChosenCalendarAndRemindersCanBeTurnedOff() {
        Fixtures.allow(application)
        FakeCalendarProvider.install().apply {
            calendar(1, "Личный")
            calendar(2, "Работа", account = "work@example.org")
        }
        val activity = open()
        assertTrue(texts(activity).containsAll(listOf("Календарь для новых событий", "Напоминать о событиях")))
        contentRoot(activity).firstOnPages<InkButton> { it.text.toString() == "Работа" }.performClick()
        assertEquals(2L, EventWriter.defaultCalendar(application, EventStore.of(application).writableCalendars())?.id)
        val reminders = contentRoot(activity).firstOnPages<InkChoiceRow<*>> { row -> row.findOnPagesTitle() == "Напоминать о событиях" }
        reminders.findButton("Выкл").performClick()
        assertFalse(Reminders.isOn(application))
    }

    @Test fun withoutAccessThereAreNoCalendarsToShow() {
        assertTrue(texts(open()).contains("На телефоне пока нет календарей"))
    }

    private fun open(): Activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun texts(activity: Activity) = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }

    private fun InkChoiceRow<*>.findOnPagesTitle() = io.github.andreygladkov.ink.descendants(this).filterIsInstance<InkTextView>().firstOrNull { it !is InkButton }?.text?.toString()

    private fun InkChoiceRow<*>.findButton(label: String) = io.github.andreygladkov.ink.descendants(this).filterIsInstance<InkButton>().single { it.text.toString() == label }
}
