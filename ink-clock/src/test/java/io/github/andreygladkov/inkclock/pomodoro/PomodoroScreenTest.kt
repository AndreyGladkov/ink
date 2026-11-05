package io.github.andreygladkov.inkclock.pomodoro

import android.app.Application
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.Tab
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class PomodoroScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PomodoroStore.of(context)

    @Test fun workStartsKeepsTheScreenOnAndStopsInBlackAndWhite() {
        val activity = open()
        assertTrue(listOf("Работа", "25 мин", "○ ○ ○ ○", "Сегодня 0 из 8").all { it in texts(activity) }, "${texts(activity)}")
        assertEquals(25, grid(activity).cells)
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Начать работу").performClick()
        assertTrue(store.pomodoro().isRunning)
        assertTrue(texts(activity).any { it.startsWith("осталось 25 мин · до ") }, "${texts(activity)}")
        assertEquals(0, grid(activity).current)
        assertTrue(keepsScreenOn(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Стоп").performClick()
        assertFalse(store.pomodoro().isRunning)
        assertFalse(keepsScreenOn(activity))
    }

    @Test fun aWaitingBreakShowsTheCycleAndTheDayAndCanBeSkipped() {
        store.save(Pomodoro(Phase.LONG_BREAK, cycleDone = 4, doneToday = 9, day = PomodoroActions.today()))
        val activity = open()
        assertTrue(listOf("Длинный перерыв", "15 мин", "● ● ● ●", "Сегодня 9 из 8").all { it in texts(activity) }, "${texts(activity)}")
        assertEquals(0 until 15, grid(activity).solid)
        assertPureBlackAndWhite(render(contentRoot(activity)))
        button(activity, "Пропустить").performClick()
        assertEquals(Pomodoro(Phase.WORK, cycleDone = 0, doneToday = 9, day = PomodoroActions.today()), store.pomodoro())
    }

    @Test fun aPhaseThatRanOutWhileAwayIsFinishedWhenShown() {
        store.save(Pomodoro().start(now = Instant.now().toEpochMilli() - 26 * 60_000L))
        val activity = open()
        assertEquals(Phase.SHORT_BREAK, store.pomodoro().phase)
        assertTrue("Начать перерыв" in contentRoot(activity).find<InkButton>().filter { it.isShown }.map { it.text.toString() })
    }

    @Test fun theScreenStaysOnOnlyWhenTheSettingAllows() {
        store.keepsScreenOn = false
        val activity = open()
        button(activity, "Начать работу").performClick()
        assertFalse(keepsScreenOn(activity))
    }

    private fun open() = Robolectric.buildActivity(MainActivity::class.java, MainActivity.intent(context, Tab.POMODORO)).setup().get()

    private fun grid(activity: MainActivity) = contentRoot(activity).find<PomodoroGridView>().single().grid!!

    private fun keepsScreenOn(activity: MainActivity) = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun button(activity: MainActivity, label: String) = contentRoot(activity).find<InkButton>().first { it.isShown && it.text.toString() == label }

    private fun texts(activity: MainActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
}
