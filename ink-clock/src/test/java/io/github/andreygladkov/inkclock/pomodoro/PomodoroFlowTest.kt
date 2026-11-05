package io.github.andreygladkov.inkclock.pomodoro

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkclock.alarm.AlarmScheduler
import io.github.andreygladkov.inkclock.notify.ActionReceiver
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class PomodoroFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PomodoroStore.of(context)
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Test fun startingWorkWakesThePhoneAtItsEndAndShowsTheEndTime() {
        PomodoroActions.start(context)
        val endsAt = store.pomodoro().endsAt(store.plan())!!
        assertEquals(endsAt, alarms.peekNextScheduledAlarm()?.triggerAtMs)
        assertTrue(titles().any { it.startsWith("Работа до ") }, "${titles()}")
    }

    @Test fun theEndOfWorkSignalsAndWaitsForTheBreakToBeStarted() {
        PomodoroActions.start(context, now = 0)
        assertFalse(PomodoroActions.finishIfDue(context, now = 60_000))
        assertTrue(PomodoroActions.finishIfDue(context, now = 25 * 60_000L))
        val pomodoro = store.pomodoro()
        assertEquals(Phase.SHORT_BREAK, pomodoro.phase)
        assertNull(pomodoro.startedAt)
        assertEquals(1, pomodoro.doneOn(PomodoroActions.today(0)))
        assertNull(alarms.peekNextScheduledAlarm())
        val done = notifications.activeNotifications.single()
        assertEquals("Работа окончена", done.notification.extras.getString("android.title"))
        assertEquals("Начать перерыв на 5 мин?", done.notification.extras.getString("android.text"))
        assertEquals("pomodoro_sound", done.notification.channelId)

        ActionReceiver().onReceive(context, Intent(ActionReceiver.ACTION_POMODORO_START))
        assertTrue(store.pomodoro().isRunning)
        assertTrue(titles().single().startsWith("Перерыв до "), "${titles()}")
    }

    @Test fun withoutSoundTheSignalOnlyVibrates() {
        store.playsSound = false
        PomodoroActions.start(context, now = 0)
        PomodoroActions.finishIfDue(context, now = 25 * 60_000L)
        assertEquals("pomodoro_quiet", notifications.activeNotifications.single().notification.channelId)
    }

    @Test fun stoppingAbandonsThePhase() {
        PomodoroActions.start(context)
        PomodoroActions.stop(context)
        assertEquals(Pomodoro(), store.pomodoro())
        assertNull(alarms.peekNextScheduledAlarm())
        assertTrue(titles().isEmpty())
    }

    @Test fun aPhaseThatEndedDuringARebootFinishesOnBoot() {
        store.save(Pomodoro().start(now = Instant.now().toEpochMilli() - 30 * 60_000L))
        AlarmScheduler.RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(Phase.SHORT_BREAK, store.pomodoro().phase)
        assertEquals("Работа окончена", titles().single())
    }

    @Test fun aRunningPhaseIsRescheduledOnBoot() {
        store.save(Pomodoro().start(now = Instant.now().toEpochMilli()))
        AlarmScheduler.RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(store.pomodoro().endsAt(store.plan()), alarms.peekNextScheduledAlarm()?.triggerAtMs)
    }

    private fun titles() = notifications.activeNotifications.map { it.notification.extras.getString("android.title").orEmpty() }
}
