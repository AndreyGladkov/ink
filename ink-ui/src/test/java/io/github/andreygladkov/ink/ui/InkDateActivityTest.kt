package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
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
class InkDateActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun theMonthOfTheDateIsShownUnderTheTitleWithTheDateSelectedAndTodayFilled() {
        val activity = open(LocalDate.of(2026, 11, 25))
        assertTrue(texts(activity).containsAll(listOf("Срок", "Ноябрь 2026")))
        val shown = days(activity).filter { it.visibility == View.VISIBLE }
        assertEquals((1..30).map(Int::toString), shown.map { it.text.toString() })
        assertEquals(listOf("25"), shown.filter { it.isSelected }.map { it.text.toString() })
        assertEquals(listOf("24"), shown.filter { it.isToday }.map { it.text.toString() })
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
        assertTrue(days(activity).none { it.isToday && it.visibility == View.VISIBLE })
        days(activity).single { it.contentDescription == "2026-12-31" }.performClick()
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(LocalDate.of(2026, 12, 31), InkDateActivity.chosen(shadowOf(activity).resultIntent))
        assertTrue(activity.isFinishing)
    }

    private fun open(date: LocalDate?) =
        Robolectric.buildActivity(InkDateActivity::class.java, InkDateActivity.intent(context, "Срок", date, today)).setup().get()

    private fun days(activity: Activity) = contentRoot(activity).find<DayCell>()

    private fun texts(activity: Activity) = contentRoot(activity).find<InkTextView>().filter { it !is InkButton && it !is DayCell }.map { it.text.toString() }
}
