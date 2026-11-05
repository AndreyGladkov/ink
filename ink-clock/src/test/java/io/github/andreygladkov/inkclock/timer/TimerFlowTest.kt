package io.github.andreygladkov.inkclock.timer

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkclock.alarm.AlarmScheduler
import io.github.andreygladkov.inkclock.ring.Ring
import io.github.andreygladkov.inkclock.ring.RingService
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class TimerFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = TimerStore.of(context)
    private val alarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var service: ServiceController<RingService>? = null

    @After fun tearDown() {
        service?.destroy()
    }

    @Test fun aStartedTimerWakesThePhoneAtItsEndAndShowsAFixedEndTime() {
        TimerActions.start(context, 300_000)
        val running = assertIs<Timer.Running>(store.timer())
        assertTrue(abs(running.endsAt - Instant.now().toEpochMilli() - 300_000) < 5_000)
        assertEquals(running.endsAt, alarmManager.peekNextScheduledAlarm()?.triggerAtMs)
        assertTrue(titles().any { it.startsWith("Таймер до ") }, "${titles()}")
    }

    @Test fun pausingCancelsTheWakeUpAndResumingSetsItAgain() {
        TimerActions.start(context, 300_000)
        TimerActions.pause(context)
        assertIs<Timer.Paused>(store.timer())
        assertNull(alarmManager.peekNextScheduledAlarm())
        assertTrue(titles().contains("Таймер на паузе, осталось 5 мин"), "${titles()}")
        TimerActions.resume(context)
        assertEquals((store.timer() as Timer.Running).endsAt, alarmManager.peekNextScheduledAlarm()?.triggerAtMs)
        TimerActions.reset(context)
        assertEquals(Timer.Idle, store.timer())
        assertTrue(titles().isEmpty())
    }

    @Test fun onlyADueTimerRings() {
        TimerActions.start(context, 300_000, label = "Чай", now = 0)
        assertEquals(Ring.OfTimer(300_000, "Чай"), TimerActions.fired(context, now = 299_500))
        assertEquals(Timer.Idle, store.timer())
        TimerActions.start(context, 300_000, now = 0)
        assertNull(TimerActions.fired(context, now = 100_000))
        assertIs<Timer.Running>(store.timer())
    }

    @Test fun aRingingTimerCanRunOneMoreMinuteOrStop() {
        TimerActions.start(context, 300_000, now = 0)
        ring()
        assertEquals(Ring.OfTimer(300_000, ""), RingService.ringing)
        RingService.addMinute()
        assertNull(RingService.ringing)
        assertEquals(60_000, (store.timer() as Timer.Running).durationMs)
        TimerActions.start(context, 300_000, now = 0)
        ring()
        RingService.stopTimer()
        assertEquals(Timer.Idle, store.timer())
        assertTrue(titles().isEmpty())
    }

    @Test fun anUnansweredTimerIsReportedMissed() {
        TimerActions.start(context, 300_000, now = 0)
        ring()
        ShadowLooper.idleMainLooper(10, TimeUnit.MINUTES)
        assertNull(RingService.ringing)
        assertTrue(titles().contains("Время вышло"))
    }

    @Test fun recentDurationsAreDistinctAndNewestFirst() {
        listOf(60_000L, 300_000L, 60_000L, 600_000L, 900_000L, 1_200_000L).forEach { TimerActions.start(context, it) }
        assertEquals(listOf(1_200_000L, 900_000L, 600_000L, 60_000L), store.recent())
    }

    @Test fun aRebootReschedulesARunningTimer() {
        store.save(Timer.start(300_000, now = Instant.now().toEpochMilli()))
        AlarmScheduler.RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals((store.timer() as Timer.Running).endsAt, alarmManager.peekNextScheduledAlarm()?.triggerAtMs)
    }

    private fun ring() {
        service?.destroy()
        val intent = Intent(context, RingService::class.java).putExtra("timer", true)
        service = Robolectric.buildService(RingService::class.java, intent).create().startCommand(0, 1)
    }

    private fun titles() = notifications.activeNotifications.map { it.notification.extras.getString("android.title").orEmpty() }
}
