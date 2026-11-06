package io.github.andreygladkov.inkclock.alarm

import android.content.Context
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.notify.ClockNotifications
import java.time.Instant

fun Alarm.ringing() = copy(snoozedUntil = null)

fun Alarm.snoozed(until: Long) = copy(snoozedUntil = until)

fun Alarm.dismissed() = copy(snoozedUntil = null, isEnabled = isRepeating)

object AlarmActions {
    fun fired(context: Context, id: Int): Alarm? {
        val store = AlarmStore.of(context)
        val alarm = store.get(id)?.ringing()
        alarm?.let(store::save)
        ClockNotifications.cancelSnoozed(context, id)
        AlarmScheduler.reschedule(context)
        return alarm
    }

    fun snooze(context: Context, alarm: Alarm, now: Long = Instant.now().toEpochMilli()) {
        val until = now + ClockPrefs.snoozeMinutes(context) * MINUTE_MS
        AlarmStore.of(context).save(alarm.snoozed(until))
        AlarmScheduler.reschedule(context)
        ClockNotifications.showSnoozed(context, alarm, until)
    }

    fun dismiss(context: Context, id: Int) {
        val store = AlarmStore.of(context)
        store.get(id)?.let { store.save(it.dismissed()) }
        ClockNotifications.cancelSnoozed(context, id)
        AlarmScheduler.reschedule(context)
    }

    fun save(context: Context, alarm: Alarm) {
        AlarmStore.of(context).save(alarm.copy(snoozedUntil = null))
        ClockNotifications.cancelSnoozed(context, alarm.id)
        AlarmScheduler.reschedule(context)
    }

    fun delete(context: Context, id: Int) {
        AlarmStore.of(context).delete(id)
        ClockNotifications.cancelSnoozed(context, id)
        AlarmScheduler.reschedule(context)
    }

    private const val MINUTE_MS = 60_000L
}
