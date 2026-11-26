package io.github.andreygladkov.inkcalendar.event

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkcalendar.FakeCalendarProvider
import io.github.andreygladkov.inkcalendar.Fixtures
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.at
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class EventActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        Fixtures.fixNow()
        Fixtures.allow(application)
        provider = FakeCalendarProvider.install()
        provider.calendar(1, "Личный")
        provider.instance(5, at(TODAY, 14), at(TODAY, 15), "Врач", location = "Поликлиника №2", notes = "Взять полис\n", rrule = "FREQ=MONTHLY")
        provider.reminder(5, 60)
        provider.reminder(5, 1440)
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun theCardShowsWhenWhereAndWhatRemindsOfTheEvent() {
        val activity = open(5, at(TODAY, 14))
        assertEquals(listOf("Событие", "Врач", "Четверг, 26 ноября", "14:00–15:00", "Повторяется", "Календарь", "Личный", "Место", "Поликлиника №2",
            "Заметки", "Взять полис", "Напоминания", "За 1 час", "За 1 день"), texts(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun anEventNoLongerThereSaysSo() {
        assertTrue(texts(open(5, at(TODAY, 16))).contains("Этого события в календаре больше нет"))
    }

    private fun open(eventId: Long, begin: Long): Activity =
        Robolectric.buildActivity(EventActivity::class.java, EventActivity.intent(application, eventId, begin)).setup().get()

    private fun texts(activity: Activity) = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }.filter { it.isNotEmpty() && !it.contains(" / ") }.distinct()
}
