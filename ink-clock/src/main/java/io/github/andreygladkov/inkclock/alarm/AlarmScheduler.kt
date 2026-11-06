package io.github.andreygladkov.inkclock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.pomodoro.PomodoroActions
import io.github.andreygladkov.inkclock.ring.RingService
import io.github.andreygladkov.inkclock.timer.TimerActions
import java.time.ZonedDateTime

data class NextAlarm(val alarm: Alarm, val at: ZonedDateTime)

object AlarmScheduler {
    private const val EXTRA_ALARM_ID = "alarm_id"
    private val RESCHEDULE_ACTIONS = setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

    fun next(alarms: List<Alarm>, now: ZonedDateTime): NextAlarm? =
        alarms.mapNotNull { alarm -> alarm.nextRing(now)?.let { NextAlarm(alarm, it) } }.minByOrNull { it.at }

    /** Only the earliest alarm is handed to the system; each ring reschedules, so the rest follow in turn. */
    fun reschedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val upcoming = next(AlarmStore.of(context).all(), ZonedDateTime.now())
        if (upcoming == null) {
            manager.cancel(fire(context, null))
            return
        }
        val show = PendingIntent.getActivity(context, 0, MainActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val info = AlarmManager.AlarmClockInfo(upcoming.at.toInstant().toEpochMilli(), show)
        manager.setAlarmClock(info, fire(context, upcoming.alarm.id))
    }

    private fun fire(context: Context, alarmId: Int?): PendingIntent {
        val intent = Intent(context, FireReceiver::class.java)
        alarmId?.let { intent.putExtra(EXTRA_ALARM_ID, it) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    class FireReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(EXTRA_ALARM_ID, -1)
            if (id >= 0) RingService.startAlarm(context, id) else reschedule(context)
        }
    }

    class RescheduleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action !in RESCHEDULE_ACTIONS) return
            reschedule(context)
            TimerActions.reschedule(context)
            PomodoroActions.reschedule(context)
        }
    }
}
