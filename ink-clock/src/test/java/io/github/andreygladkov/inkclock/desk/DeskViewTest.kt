package io.github.andreygladkov.inkclock.desk

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.alarm.Alarm
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.alarm.AlarmStore
import io.github.andreygladkov.inkclock.pomodoro.Phase
import io.github.andreygladkov.inkclock.pomodoro.Pomodoro
import io.github.andreygladkov.inkclock.pomodoro.PomodoroDial
import io.github.andreygladkov.inkclock.pomodoro.PomodoroStore
import io.github.andreygladkov.inkclock.pomodoro.TomatoView
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class DeskViewTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun showsTheTimeTheDateAndTheNextAlarmInBlackAndWhite() {
        AlarmStore.of(context).save(Alarm(1, 7, 0))
        val activity = open()
        val now = ZonedDateTime.now()
        val texts = texts(activity)
        assertTrue(AlarmLabels(context).time(now) in texts, "$texts")
        assertTrue(texts.any { it.endsWith(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")).format(now)) }, "$texts")
        assertTrue(texts.any { it.startsWith("Будильник ") && it.endsWith("07:00") }, "$texts")
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun withoutAlarmsThereIsNoAlarmLine() {
        assertFalse(texts(open()).any { it.startsWith("Будильник") })
    }

    @Test fun aRunningPomodoroTakesThePlaceOfTheClock() {
        PomodoroStore.of(context).save(Pomodoro(Phase.SHORT_BREAK).start(now = Instant.now().toEpochMilli() - 60_000))
        val activity = open()
        val texts = texts(activity)
        assertTrue("Перерыв" in texts && texts.any { it.startsWith("осталось 4 мин") }, "$texts")
        assertEquals(PomodoroDial(5, black = 1, refills = true), contentRoot(activity).find<TomatoView>().single().dial)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun redrawsAtTheNextMinuteOfTheClockOrOfThePomodoro() {
        assertEquals(15_000, DeskView.untilNextMinute(nowMs = 120_000 + 45_000, pomodoroStartedAt = null))
        assertEquals(5_000, DeskView.untilNextMinute(nowMs = 120_000 + 45_000, pomodoroStartedAt = 110_000))
    }

    @Test fun theScreensaverCountsAsChosenOnlyWhenTheSystemListsIt() {
        assertFalse(ClockDreamSetting.isSelected(context))
        Settings.Secure.putString(context.contentResolver, "screensaver_components",
            "com.android.deskclock/.Screensaver," + ComponentName(context, ClockDream::class.java).flattenToString())
        assertTrue(ClockDreamSetting.isSelected(context))
    }

    private fun open() = Robolectric.buildActivity(DeskClockActivity::class.java, DeskClockActivity.intent(context)).setup().get()

    private fun texts(activity: DeskClockActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
}
