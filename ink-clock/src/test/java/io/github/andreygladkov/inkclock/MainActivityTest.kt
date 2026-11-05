package io.github.andreygladkov.inkclock

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.alarm.Alarm
import io.github.andreygladkov.inkclock.alarm.AlarmActions
import io.github.andreygladkov.inkclock.alarm.AlarmEditActivity
import io.github.andreygladkov.inkclock.alarm.AlarmStore
import io.github.andreygladkov.inkclock.desk.DeskClockActivity
import io.github.andreygladkov.inkclock.ring.RingActivity
import io.github.andreygladkov.inkclock.ring.RingService
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = AlarmStore.of(context)

    @Test fun listsAlarmsByTimeWithTheNextOneInBlackAndWhite() {
        store.save(Alarm(1, 9, 0, isEnabled = false))
        store.save(Alarm(2, 6, 30, days = DayOfWeek.entries.toSet(), label = "Работа"))
        val activity = open()
        val texts = texts(activity)
        assertTrue(texts.indexOf("06:30") in 0 until texts.indexOf("09:00"), "$texts")
        assertTrue("Каждый день · Работа" in texts)
        assertTrue(texts.any { it.startsWith("Следующий: ") && "06:30" in it }, "$texts")
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun withoutAlarmsSaysSo() {
        val texts = texts(open())
        assertTrue("Будильников нет" in texts)
        assertTrue("Будильники не заведены" in texts)
    }

    @Test fun theToggleTurnsAnAlarmOnAndOff() {
        store.save(Alarm(1, 7, 0))
        val activity = open()
        toggle(activity).performClick()
        assertFalse(store.get(1)!!.isEnabled)
        assertEquals("Выкл", toggle(activity).text.toString())
        toggle(activity).performClick()
        assertTrue(store.get(1)!!.isEnabled)
    }

    @Test fun tappingAnAlarmOpensItsEditor() {
        store.save(Alarm(1, 7, 0))
        val activity = open()
        (toggle(activity).parent as android.view.View).performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(AlarmEditActivity::class.java.name, started.component?.className)
        assertEquals(1, started.getIntExtra("alarm_id", -1))
    }

    @Test fun aRingWhileTheAppIsOpenShowsTheRingScreen() {
        AlarmActions.save(context, Alarm(1, 7, 0))
        val activity = open()
        val service = Robolectric.buildService(RingService::class.java, Intent(context, RingService::class.java).putExtra(RingService.EXTRA_ALARM_ID, 1))
            .create().startCommand(0, 1)
        assertEquals(RingActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        service.destroy()
    }

    @Test fun theClockButtonOpensTheDeskClock() {
        val activity = open()
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Настольные часы" }.performClick()
        assertEquals(DeskClockActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    private fun open() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun toggle(activity: MainActivity) = contentRoot(activity).find<InkButton>().single { it.contentDescription == "Будильник на 07:00" }

    private fun texts(activity: MainActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }
}
