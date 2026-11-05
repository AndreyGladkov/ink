package io.github.andreygladkov.inkclock.ring

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
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
import io.github.andreygladkov.inkclock.alarm.AlarmStore
import io.github.andreygladkov.inkclock.timer.TimerActions
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class RingActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = AlarmStore.of(context)
    private lateinit var service: ServiceController<RingService>

    @Before fun ring() {
        AlarmActions.save(context, Alarm(1, 7, 0, label = "Подъём"))
        service = Robolectric.buildService(RingService::class.java, Intent(context, RingService::class.java).putExtra(RingService.EXTRA_ALARM_ID, 1))
            .create().startCommand(0, 1)
    }

    @After fun tearDown() = service.destroy().let { }

    @Test fun showsTheAlarmInBlackAndWhiteAndSnoozes() {
        val activity = open()
        val texts = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
        assertTrue("07:00" in texts && "Подъём" in texts, "$texts")
        assertPureBlackAndWhite(render(contentRoot(activity)))
        button(activity, "Отложить").performClick()
        assertNotNull(store.get(1)!!.snoozedUntil)
        assertTrue(activity.isFinishing)
    }

    @Test fun aTapDoesNotDismissButHoldingDoes() {
        val activity = open()
        val dismiss = button(activity, "Удерживайте, чтобы отключить")
        touch(dismiss, MotionEvent.ACTION_DOWN)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        touch(dismiss, MotionEvent.ACTION_UP)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertNotNull(RingService.ringing)
        touch(dismiss, MotionEvent.ACTION_DOWN)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertNull(RingService.ringing)
        assertFalse(store.get(1)!!.isEnabled)
        assertTrue(activity.isFinishing)
    }

    @Test fun aRingingTimerStopsWithATap() {
        RingService.dismiss(1)
        TimerActions.start(context, 300_000, label = "Чай", now = 0)
        service.destroy()
        service = Robolectric.buildService(RingService::class.java, Intent(context, RingService::class.java).putExtra("timer", true))
            .create().startCommand(0, 1)
        val activity = open()
        val texts = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
        assertTrue("Время вышло" in texts && "Чай" in texts, "$texts")
        assertPureBlackAndWhite(render(contentRoot(activity)))
        button(activity, "Стоп").performClick()
        assertNull(RingService.ringing)
        assertTrue(activity.isFinishing)
    }

    private fun open() = Robolectric.buildActivity(RingActivity::class.java, RingActivity.intent(context)).setup().get()

    private fun button(activity: RingActivity, label: String) = contentRoot(activity).find<InkButton>().single { it.text.toString() == label }

    private fun touch(button: InkButton, action: Int) {
        val now = SystemClock.uptimeMillis()
        button.dispatchTouchEvent(MotionEvent.obtain(now, now, action, button.width / 2f, button.height / 2f, 0))
    }
}
