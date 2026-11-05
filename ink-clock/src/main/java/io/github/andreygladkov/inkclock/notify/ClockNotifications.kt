package io.github.andreygladkov.inkclock.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.Tab
import io.github.andreygladkov.inkclock.alarm.Alarm
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.ring.Ring
import io.github.andreygladkov.inkclock.ring.RingActivity
import io.github.andreygladkov.inkclock.stopwatch.Stopwatch
import io.github.andreygladkov.inkclock.stopwatch.StopwatchLabels
import io.github.andreygladkov.inkclock.timer.Timer
import io.github.andreygladkov.inkclock.timer.TimerLabels
import java.time.Instant
import java.time.ZoneId

/** Ongoing notifications carry fixed text with no chronometer, so an open shade does not repaint the panel every second. */
object ClockNotifications {
    const val ID_RINGING = 1
    private const val ID_TIMER = 2
    private const val ID_STOPWATCH = 3
    private const val ID_TIMER_MISSED = 4
    private const val CHANNEL_RINGING = "ringing"
    private const val CHANNEL_STATUS = "status"
    private const val CHANNEL_MISSED = "missed"
    private const val SNOOZED_ID_BASE = 1000
    private const val MISSED_ID_BASE = 2000

    fun createChannels(context: Context) {
        manager(context).createNotificationChannels(listOf(
            // RingService plays the sound itself, so a channel sound would ring over it.
            NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) },
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    fun ringing(context: Context, ring: Ring): Notification {
        val screen = PendingIntent.getActivity(context, 0, RingActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
        when (ring) {
            is Ring.OfAlarm -> builder
                .setContentTitle(AlarmLabels(context).time(ring.alarm.hour, ring.alarm.minute))
                .setContentText(ring.alarm.label.ifBlank { context.getString(R.string.alarm) })
                .addAction(action(context, ActionReceiver.ACTION_SNOOZE, R.string.snooze, ring.alarm.id))
                .addAction(action(context, ActionReceiver.ACTION_DISMISS, R.string.dismiss, ring.alarm.id))
            is Ring.OfTimer -> builder
                .setContentTitle(context.getString(R.string.timer_done))
                .setContentText(ring.label.ifBlank { TimerLabels(context).duration(ring.durationMs) })
                .addAction(action(context, ActionReceiver.ACTION_TIMER_ADD_MINUTE, R.string.add_minute))
                .addAction(action(context, ActionReceiver.ACTION_TIMER_STOP, R.string.stop))
        }
        return builder.build()
    }

    fun showSnoozed(context: Context, alarm: Alarm, until: Long) {
        val notification = status(context, Tab.ALARMS)
            .setContentTitle(context.getString(R.string.snoozed_until, AlarmLabels(context).time(zoned(until))))
            .setContentText(alarm.label.ifBlank { null })
            .addAction(action(context, ActionReceiver.ACTION_CANCEL_SNOOZE, R.string.dismiss, alarm.id))
            .build()
        manager(context).notify(SNOOZED_ID_BASE + alarm.id, notification)
    }

    fun cancelSnoozed(context: Context, alarmId: Int) = manager(context).cancel(SNOOZED_ID_BASE + alarmId)

    fun showMissed(context: Context, alarm: Alarm) {
        val notification = missed(context, Tab.ALARMS)
            .setContentTitle(context.getString(R.string.missed_alarm, AlarmLabels(context).time(alarm.hour, alarm.minute)))
            .setContentText(alarm.label.ifBlank { null })
            .build()
        manager(context).notify(MISSED_ID_BASE + alarm.id, notification)
    }

    fun showTimerMissed(context: Context, ring: Ring.OfTimer) {
        val notification = missed(context, Tab.TIMER)
            .setContentTitle(context.getString(R.string.timer_done))
            .setContentText(ring.label.ifBlank { TimerLabels(context).duration(ring.durationMs) })
            .build()
        manager(context).notify(ID_TIMER_MISSED, notification)
    }

    fun showTimer(context: Context, timer: Timer) {
        val labels = TimerLabels(context)
        val builder = when (timer) {
            Timer.Idle -> return manager(context).cancel(ID_TIMER)
            is Timer.Running -> status(context, Tab.TIMER)
                .setContentTitle(context.getString(R.string.timer_running, AlarmLabels(context).time(zoned(timer.endsAt))))
                .addAction(action(context, ActionReceiver.ACTION_TIMER_PAUSE, R.string.pause))
            is Timer.Paused -> status(context, Tab.TIMER)
                .setContentTitle(context.getString(R.string.timer_paused, labels.remaining(timer.remainingMs)))
                .addAction(action(context, ActionReceiver.ACTION_TIMER_RESUME, R.string.resume))
        }
        builder.addAction(action(context, ActionReceiver.ACTION_TIMER_RESET, R.string.reset))
        manager(context).notify(ID_TIMER, builder.build())
    }

    fun showStopwatch(context: Context, stopwatch: Stopwatch) {
        val title = when {
            stopwatch.isIdle -> return manager(context).cancel(ID_STOPWATCH)
            stopwatch.isRunning -> context.getString(R.string.stopwatch_since,
                AlarmLabels(context).time(zoned(stopwatch.startedAt!! - stopwatch.elapsedMs)))
            else -> context.getString(R.string.stopwatch_paused, StopwatchLabels.elapsed(stopwatch.elapsedMs, tenths = false))
        }
        manager(context).notify(ID_STOPWATCH, status(context, Tab.STOPWATCH).setContentTitle(title).build())
    }

    private fun status(context: Context, tab: Tab) = Notification.Builder(context, CHANNEL_STATUS)
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(Notification.CATEGORY_STATUS)
        .setOngoing(true)
        .setContentIntent(open(context, tab))

    private fun missed(context: Context, tab: Tab) = Notification.Builder(context, CHANNEL_MISSED)
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(Notification.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(open(context, tab))

    private fun open(context: Context, tab: Tab) =
        PendingIntent.getActivity(context, tab.ordinal, MainActivity.intent(context, tab), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(context: Context, action: String, title: Int, alarmId: Int = 0): Notification.Action {
        val intent = Intent(action).setClass(context, ActionReceiver::class.java).putExtra(ActionReceiver.EXTRA_ALARM_ID, alarmId)
        val pending = PendingIntent.getBroadcast(context, action.hashCode() + alarmId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), pending).build()
    }

    private fun zoned(epochMs: Long) = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
