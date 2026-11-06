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
import io.github.andreygladkov.inkclock.pomodoro.Phase
import io.github.andreygladkov.inkclock.pomodoro.Pomodoro
import io.github.andreygladkov.inkclock.pomodoro.PomodoroLabels
import io.github.andreygladkov.inkclock.pomodoro.PomodoroPlan
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
    private const val ID_POMODORO = 5
    private const val ID_POMODORO_DONE = 6
    private const val CHANNEL_RINGING = "ringing"
    private const val CHANNEL_STATUS = "status"
    private const val CHANNEL_MISSED = "missed"
    private const val CHANNEL_POMODORO_SOUND = "pomodoro_sound"
    private const val CHANNEL_POMODORO_QUIET = "pomodoro_quiet"
    private val POMODORO_VIBRATION = longArrayOf(0, 300, 200, 300)
    private const val SNOOZED_ID_BASE = 1000
    private const val MISSED_ID_BASE = 2000

    fun createChannels(context: Context) {
        manager(context).createNotificationChannels(listOf(
            // RingService plays the sound itself, so a channel sound would ring over it.
            NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) },
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_POMODORO_SOUND, context.getString(R.string.channel_pomodoro_sound), NotificationManager.IMPORTANCE_HIGH)
                .apply { vibrationPattern = POMODORO_VIBRATION },
            NotificationChannel(CHANNEL_POMODORO_QUIET, context.getString(R.string.channel_pomodoro_quiet), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                vibrationPattern = POMODORO_VIBRATION
            },
        ))
    }

    fun ringing(context: Context, ring: Ring): Notification {
        val screen = PendingIntent.getActivity(context, 0, RingActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(smallIcon(if (ring is Ring.OfTimer) Tab.TIMER else Tab.ALARMS))
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

    fun showPomodoro(context: Context, pomodoro: Pomodoro, plan: PomodoroPlan) {
        val endsAt = pomodoro.endsAt(plan) ?: return manager(context).cancel(ID_POMODORO)
        val notification = status(context, Tab.POMODORO)
            .setContentTitle(context.getString(R.string.pomodoro_until, PomodoroLabels.phase(context, pomodoro.phase), AlarmLabels(context).time(zoned(endsAt))))
            .addAction(action(context, ActionReceiver.ACTION_POMODORO_STOP, R.string.stop))
            .build()
        manager(context).notify(ID_POMODORO, notification)
    }

    /** The phase-end signal is this notification's channel sound and vibration, so the setting picks the channel. */
    fun showPomodoroDone(context: Context, ended: Phase, next: Pomodoro, plan: PomodoroPlan, sound: Boolean) {
        val notification = Notification.Builder(context, if (sound) CHANNEL_POMODORO_SOUND else CHANNEL_POMODORO_QUIET)
            .setSmallIcon(smallIcon(Tab.POMODORO))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open(context, Tab.POMODORO))
            .setContentTitle(context.getString(if (ended == Phase.WORK) R.string.pomodoro_work_done else R.string.pomodoro_break_done))
            .setContentText(PomodoroLabels.nextPrompt(context, next.phase, plan))
            .addAction(action(context, ActionReceiver.ACTION_POMODORO_START, R.string.start))
            .build()
        manager(context).notify(ID_POMODORO_DONE, notification)
    }

    fun cancelPomodoroDone(context: Context) = manager(context).cancel(ID_POMODORO_DONE)

    private fun status(context: Context, tab: Tab) = Notification.Builder(context, CHANNEL_STATUS)
        .setSmallIcon(smallIcon(tab))
        .setCategory(Notification.CATEGORY_STATUS)
        .setOngoing(true)
        .setContentIntent(open(context, tab))

    private fun missed(context: Context, tab: Tab) = Notification.Builder(context, CHANNEL_MISSED)
        .setSmallIcon(smallIcon(tab))
        .setCategory(Notification.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setContentIntent(open(context, tab))

    // The status bar draws a small icon from its alpha channel alone, so each tab's black icon serves as is.
    private fun smallIcon(tab: Tab) = tab.icon

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
