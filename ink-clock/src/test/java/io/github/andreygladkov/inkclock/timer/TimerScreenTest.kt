package io.github.andreygladkov.inkclock.timer

import android.app.Application
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.Tab
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class TimerScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = TimerStore.of(context)

    @Test fun aTimerIsTypedStartedPausedAndResetInBlackAndWhite() {
        val activity = open()
        assertTrue("00ч 00м 00с" in texts(activity), "${texts(activity)}")
        listOf("5", "00").forEach { button(activity, it).performClick() }
        assertTrue("00ч 05м 00с" in texts(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Старт").performClick()
        assertIs<Timer.Running>(store.timer())
        assertTrue("5 мин" in texts(activity) && texts(activity).any { it.startsWith("до ") }, "${texts(activity)}")
        assertFalse(button(activity, "Новый будильник").isShown)
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Пауза").performClick()
        assertTrue("на паузе" in texts(activity))
        button(activity, "+1 мин").performClick()
        assertEquals(360_000, (store.timer() as Timer.Paused).durationMs)
        button(activity, "Сброс").performClick()
        assertEquals(Timer.Idle, store.timer())
        assertTrue(button(activity, "5 мин").isShown)
    }

    @Test fun recentDurationsAreBorderlessAndDividedAndStartAtOnce() {
        listOf(5_000L, 55_000L, 60_000L, 90_000L).forEach(store::remember)
        val activity = open()
        val recent = listOf("1 мин 30 с", "1 мин", "55 с", "5 с").map { button(activity, it) }
        assertTrue(recent.none { it.isBordered })
        val row = recent.first().parent as InkButtonRow
        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE, row.showDividers)
        assertEquals(recent, (0 until row.childCount).map(row::getChildAt))
        assertPureBlackAndWhite(render(contentRoot(activity)))
        recent.first().performClick()
        assertEquals(90_000, (store.timer() as Timer.Running).durationMs)
    }

    @Test fun aLabelledTimerShowsItsLabel() {
        TimerActions.start(context, 300_000, label = "Чай")
        val activity = open()
        assertTrue(texts(activity).any { it.startsWith("Чай · до ") }, "${texts(activity)}")
        button(activity, "Пауза").performClick()
        assertTrue("Чай · на паузе" in texts(activity))
    }

    @Test fun aLabelThatChangesOnPauseStillFitsItsButton() {
        TimerActions.start(context, 300_000)
        val activity = open()
        render(contentRoot(activity))
        button(activity, "Пауза").performClick()
        render(contentRoot(activity))
        val resume = button(activity, "Продолжить")
        val available = resume.width - resume.paddingLeft - resume.paddingRight
        assertTrue(resume.paint.measureText(resume.text.toString()) <= available, "'${resume.text}' overflows $available px")
    }

    @Test fun anEmptyEntryDoesNotStart() {
        val activity = open()
        button(activity, "Старт").performClick()
        assertEquals(Timer.Idle, store.timer())
    }

    private fun open() = Robolectric.buildActivity(MainActivity::class.java, MainActivity.intent(context, Tab.TIMER)).setup().get()

    private fun button(activity: MainActivity, label: String) =
        contentRoot(activity).find<InkButton>().filter { it.isShown || label == "Новый будильник" }.first { it.text.toString() == label }

    private fun texts(activity: MainActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
}
