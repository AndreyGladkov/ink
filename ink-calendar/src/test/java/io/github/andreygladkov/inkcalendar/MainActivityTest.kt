package io.github.andreygladkov.inkcalendar

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.DayCell
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkcalendar.Fixtures.TODAY
import io.github.andreygladkov.inkcalendar.Fixtures.at
import io.github.andreygladkov.inkcalendar.Fixtures.utcDay
import io.github.andreygladkov.inkcalendar.event.EditActivity
import io.github.andreygladkov.inkcalendar.event.EventActivity
import io.github.andreygladkov.inkcalendar.ui.SwipeFrame
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var provider: FakeCalendarProvider

    @Before fun setUp() {
        Fixtures.fixNow()
        provider = FakeCalendarProvider.install()
        provider.calendar(1, "Личный")
        provider.instance(1, at(TODAY, 19), at(TODAY, 20), "Ужин")
        provider.instance(2, at(TODAY, 9), at(TODAY, 10, 30), "Стендап")
        provider.instance(3, utcDay(TODAY), utcDay(TODAY.plusDays(1)), "День рождения", allDay = true)
        provider.instance(4, at(TODAY.plusDays(2), 11), at(TODAY.plusDays(2), 12), "Созвон")
        provider.instance(5, at(TODAY.plusDays(10), 11), at(TODAY.plusDays(10), 12), "Врач")
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun withoutAccessTheScreenExplainsAndAsksForIt() {
        val activity = open()
        assertTrue(shown(activity).any { it.startsWith("Ink Calendar показывает события") })
        assertTrue(buttons(activity).any { it.text.toString() == "Разрешить доступ" && it.isShown })
        assertTrue(days(activity).none { it.isShown })
    }

    @Test fun theMonthOpensOnTodayWithItsEventsBelowAndDotsOnDaysWithEvents() {
        Fixtures.allow(application)
        val activity = open()
        assertTrue(shown(activity).containsAll(listOf("Ноябрь 2026", "Четверг, 26 ноября")))
        assertEquals(listOf("26"), days(activity).filter { it.isToday && it.isSelected }.map { it.text.toString() })
        assertEquals(listOf("26", "28"), days(activity).filter { it.isMarked && it.isShown }.map { it.text.toString() })
        assertEquals(listOf("День рождения\nВесь день", "Стендап\n9:00–10:30", "Ужин\n19:00–20:00"), rows(activity))
        assertFalse(button(activity, "Сегодня").isShown)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aTappedDayShowsItsEventsAndTodayBringsTheScreenBack() {
        Fixtures.allow(application)
        val activity = open()
        day(activity, "2026-11-28").performClick()
        assertTrue(shown(activity).contains("Суббота, 28 ноября"))
        assertEquals(listOf("Созвон\n11:00–12:00"), rows(activity))
        day(activity, "2026-11-27").performClick()
        assertTrue(shown(activity).contains("Нет событий"))
        button(activity, "Сегодня").performClick()
        assertEquals(3, rows(activity).size)
        assertFalse(button(activity, "Сегодня").isShown)
    }

    @Test fun arrowsAndSwipesTurnWholeMonthsAndOpenTheirFirstDay() {
        Fixtures.allow(application)
        val activity = open()
        buttons(activity).single { it.contentDescription == "Следующий месяц" }.performClick()
        assertTrue(shown(activity).containsAll(listOf("Декабрь 2026", "Вторник, 1 декабря")))
        assertEquals(listOf("6"), days(activity).filter { it.isMarked && it.isShown }.map { it.text.toString() })
        assertTrue(button(activity, "Сегодня").isShown)
        swipe(activity, upwards = true)
        assertTrue(shown(activity).contains("Январь 2027"))
        swipe(activity, upwards = false)
        swipe(activity, upwards = false)
        assertTrue(shown(activity).containsAll(listOf("Ноябрь 2026", "Четверг, 26 ноября")))
    }

    @Test fun aTappedEventOpensItsCard() {
        Fixtures.allow(application)
        val activity = open()
        buttons(activity).single { it.text.toString().startsWith("Ужин") }.performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(EventActivity::class.java.name, started.component?.className)
        assertEquals(1L, started.getLongExtra("event", -1))
        assertEquals(at(TODAY, 19), started.getLongExtra("begin", -1))
    }

    @Test fun plusCreatesAnEventOnTheChosenDay() {
        Fixtures.allow(application)
        val activity = open()
        day(activity, "2026-11-28").performClick()
        buttons(activity).single { it.contentDescription == "Новое событие" }.performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(EditActivity::class.java.name, started.component?.className)
        assertEquals(TODAY.plusDays(2).toEpochDay(), started.getLongExtra("date", -1))
    }

    @Test fun theMonthTitleOpensTheYearAndAChosenMonthComesBack() {
        Fixtures.allow(application)
        val activity = open()
        button(activity, "Ноябрь 2026").performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(YearActivity::class.java.name, asked.intent.component?.className)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().putExtra("month", "2027-03"))
        assertTrue(shown(activity).containsAll(listOf("Март 2027", "Понедельник, 1 марта")))
    }

    @Test fun aStyleChangedWhileAwayRebuildsTheScreenWithoutFailingToStopListening() {
        Fixtures.allow(application)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause().stop()
        application.inkPrefs().edit { putFloat(InkStyle.PREF_TEXT_SIZE_SP, 24f) }
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idle()
        val rebuilt = controller.get()
        assertTrue(contentRoot(rebuilt).find<InkTextView>().all { it.style.textSizeSp == 24f })
        Fixtures.fixNow(TODAY.plusDays(1).atTime(9, 0))
        application.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("27"), days(rebuilt).filter { it.isToday }.map { it.text.toString() })
        controller.pause()
    }

    private fun open(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun swipe(activity: Activity, upwards: Boolean) {
        val frame = contentRoot(activity).find<SwipeFrame>().single()
        val time = SystemClock.uptimeMillis()
        val (from, to) = if (upwards) 600f to 100f else 100f to 600f
        listOf(MotionEvent.ACTION_DOWN to from, MotionEvent.ACTION_MOVE to (from + to) / 2, MotionEvent.ACTION_UP to to).forEach { (action, y) ->
            frame.dispatchTouchEvent(MotionEvent.obtain(time, time, action, 400f, y, 0))
        }
    }

    private fun days(activity: Activity) = contentRoot(activity).find<DayCell>()

    private fun day(activity: Activity, date: String) = days(activity).single { it.contentDescription == date }

    private fun buttons(activity: Activity) = contentRoot(activity).find<InkButton>()

    private fun button(activity: Activity, label: String) = buttons(activity).single { it.text.toString() == label }

    private fun rows(activity: Activity) = buttons(activity).filter { it.isShown && it.text.contains('\n') }.map { it.text.toString() }

    private fun shown(activity: Activity) = contentRoot(activity).find<InkTextView>().filter { it.isShown && it.visibility == View.VISIBLE }.map { it.text.toString() }
}
