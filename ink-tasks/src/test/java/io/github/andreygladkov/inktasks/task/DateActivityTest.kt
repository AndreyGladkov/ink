package io.github.andreygladkov.inktasks.task

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inktasks.Fixtures
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class DateActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before fun fixToday() = Fixtures.fixToday()

    @After fun restore() = Fixtures.restoreClock()

    @Test fun theMonthOfTheDateIsShownFromMondayWithTheDateSelectedAndTodayBold() {
        val activity = open(LocalDate.of(2026, 11, 25))
        assertTrue(texts(activity).contains("Ноябрь 2026"))
        assertEquals("пн", texts(activity).first { it.length == 2 }.lowercase())
        val shown = days(activity).filter { it.visibility == View.VISIBLE }
        assertEquals((1..30).map(Int::toString), shown.map { it.text.toString() })
        assertEquals(listOf("25"), shown.filter { it.isSelected }.map { it.text.toString() })
        assertEquals(listOf("24"), shown.filter { it.isBold }.map { it.text.toString() })
        assertEquals(View.INVISIBLE, days(activity).first().visibility)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun withoutADateTodaysMonthOpensWithNothingSelected() {
        val activity = open(null)
        assertTrue(texts(activity).contains("Ноябрь 2026"))
        assertTrue(days(activity).none { it.isSelected })
    }

    @Test fun arrowsChangeTheMonthAndATapChoosesTheDay() {
        val activity = open(LocalDate.of(2026, 11, 25))
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Следующий месяц" }.performClick()
        assertTrue(texts(activity).contains("Декабрь 2026"))
        days(activity).single { it.contentDescription == "2026-12-31" }.performClick()
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(LocalDate.of(2026, 12, 31), DateActivity.chosen(shadowOf(activity).resultIntent))
        assertTrue(activity.isFinishing)
    }

    private fun open(date: LocalDate?) = Robolectric.buildActivity(DateActivity::class.java, DateActivity.intent(context, date)).setup().get()

    private fun days(activity: DateActivity) = contentRoot(activity).find<InkButton>().filter { it.contentDescription?.matches(Regex("""\d{4}-\d{2}-\d{2}""")) == true }

    private fun texts(activity: DateActivity) = contentRoot(activity).find<InkTextView>().filter { it !is InkButton }.map { it.text.toString() }
}
