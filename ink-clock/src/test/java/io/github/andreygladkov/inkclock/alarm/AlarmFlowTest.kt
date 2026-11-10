package io.github.andreygladkov.inkclock.alarm

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkclock.notify.ActionReceiver
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class AlarmFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = AlarmStore.of(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var service: ServiceController<RingService>? = null

    @After fun tearDown() {
        service?.destroy()
    }

    @Test fun theStoreKeepsEveryField() {
        val alarm = Alarm(3, 6, 45, setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), "Работа", isEnabled = false,
            ringtone = Ringtone.Sound("content://media/internal/audio/media/7"), vibrates = false, snoozedUntil = 123L)
        store.save(alarm)
        store.save(Alarm(4, 5, 0, ringtone = Ringtone.Silent))
        assertEquals(listOf(Alarm(4, 5, 0, ringtone = Ringtone.Silent), alarm), store.all())
        assertEquals(5, store.nextId())
        store.delete(3)
        assertEquals(listOf(4), store.all().map { it.id })
    }

    @Test fun savingAnAlarmHandsTheEarliestRingToTheSystem() {
        AlarmActions.save(context, Alarm(1, 9, 0))
        AlarmActions.save(context, Alarm(2, 6, 30))
        // Which of the two rings first depends on the time of day the test runs at.
        val (earliest, later) = listOf(1, 2).sortedBy(::nextRingMillis)
        assertEquals(nextRingMillis(earliest), alarmManager.nextAlarmClock?.triggerTime)
        AlarmActions.delete(context, earliest)
        assertEquals(nextRingMillis(later), alarmManager.nextAlarmClock?.triggerTime)
    }

    @Test fun firingRingsInTheForegroundUntilSnoozed() {
        AlarmActions.save(context, Alarm(1, 7, 0, label = "Подъём"))
        fire(1)
        assertEquals(1, (RingService.ringing as Ring.OfAlarm).alarm.id)
        val ringing = shadowOf(service!!.get()).lastForegroundNotification
        assertEquals("07:00", ringing.extras.getString("android.title"))
        assertNotNull(ringing.fullScreenIntent)

        RingService.snooze(1)
        assertNull(RingService.ringing)
        assertTrue(shadowOf(service!!.get()).isStoppedBySelf)
        val snoozedUntil = store.get(1)!!.snoozedUntil!!
        assertTrue(abs(snoozedUntil - Instant.now().plus(Duration.ofMinutes(10)).toEpochMilli()) < 5_000)
        assertEquals(snoozedUntil, alarmManager.nextAlarmClock?.triggerTime)
        assertTrue(notifications.activeNotifications.any { it.notification.extras.getString("android.title")!!.startsWith("Отложен до") })
    }

    @Test fun dismissingAOneTimeAlarmTurnsItOff() {
        AlarmActions.save(context, Alarm(1, 7, 0))
        fire(1)
        RingService.dismiss(1)
        assertFalse(store.get(1)!!.isEnabled)
        assertNull(alarmManager.nextAlarmClock)
    }

    @Test fun dismissingARepeatingAlarmKeepsItForItsNextDay() {
        AlarmActions.save(context, Alarm(1, 7, 0, days = DayOfWeek.entries.toSet()))
        fire(1)
        RingService.dismiss(1)
        assertTrue(store.get(1)!!.isEnabled)
        assertEquals(nextRingMillis(1), alarmManager.nextAlarmClock?.triggerTime)
    }

    @Test fun anUnansweredAlarmStopsAndIsReportedMissed() {
        AlarmActions.save(context, Alarm(1, 7, 0))
        fire(1)
        ShadowLooper.idleMainLooper(10, TimeUnit.MINUTES)
        assertNull(RingService.ringing)
        assertFalse(store.get(1)!!.isEnabled)
        assertTrue(notifications.activeNotifications.any { it.notification.extras.getString("android.title") == "Пропущен будильник 07:00" })
    }

    @Test fun theSnoozeNotificationCancelsTheSnooze() {
        AlarmActions.save(context, Alarm(1, 7, 0))
        AlarmActions.snooze(context, store.get(1)!!)
        ActionReceiver().onReceive(context, Intent(ActionReceiver.ACTION_CANCEL_SNOOZE).putExtra(ActionReceiver.EXTRA_ALARM_ID, 1))
        assertNull(store.get(1)!!.snoozedUntil)
        assertFalse(store.get(1)!!.isEnabled)
        assertTrue(notifications.activeNotifications.isEmpty())
    }

    @Test fun aRebootReschedulesTheAlarms() {
        store.save(Alarm(1, 8, 15))
        assertNull(alarmManager.nextAlarmClock)
        AlarmScheduler.RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(nextRingMillis(1), alarmManager.nextAlarmClock?.triggerTime)
    }

    @Test fun beforeTheFirstUnlockAlarmsAreRescheduledAndRingFromDeviceStorage() {
        AlarmActions.save(context, Alarm(1, 7, 0))
        val deviceStore = AlarmStore(context.createDeviceProtectedStorageContext().getSharedPreferences("alarms", 0))
        assertEquals(listOf(1), deviceStore.all().map { it.id })
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        AlarmScheduler.RescheduleReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(nextRingMillis(1), alarmManager.nextAlarmClock?.triggerTime)
        fire(1)
        RingService.snooze(1)
        assertNotNull(deviceStore.get(1)!!.snoozedUntil)
    }

    private fun fire(id: Int) {
        val intent = Intent(context, RingService::class.java).putExtra(RingService.EXTRA_ALARM_ID, id)
        service = Robolectric.buildService(RingService::class.java, intent).create().startCommand(0, 1)
    }

    private fun nextRingMillis(id: Int) = store.get(id)!!.nextRing(ZonedDateTime.now())!!.toInstant().toEpochMilli()
}
