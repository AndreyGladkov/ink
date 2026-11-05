package io.github.andreygladkov.inkclock.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.alarm.Alarm
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.alarm.RingActivity
import java.time.Instant
import java.time.ZoneId

object ClockNotifications {
    const val ID_RINGING = 1
    private const val CHANNEL_RINGING = "ringing"
    private const val CHANNEL_STATUS = "status"
    private const val CHANNEL_MISSED = "missed"
    private const val SNOOZED_ID_BASE = 1000
    private const val MISSED_ID_BASE = 2000

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            // RingService plays the alarm sound itself, so a channel sound would ring over it.
            NotificationChannel(CHANNEL_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) },
            NotificationChannel(CHANNEL_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    fun ringing(context: Context, alarm: Alarm): Notification {
        val labels = AlarmLabels(context)
        val screen = PendingIntent.getActivity(context, 0, RingActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(labels.time(alarm.hour, alarm.minute))
            .setContentText(alarm.label.ifBlank { context.getString(R.string.alarm) })
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(action(context, ActionReceiver.ACTION_SNOOZE, alarm.id, R.string.snooze))
            .addAction(action(context, ActionReceiver.ACTION_DISMISS, alarm.id, R.string.dismiss))
            .build()
    }

    fun showSnoozed(context: Context, alarm: Alarm, until: Long) {
        val labels = AlarmLabels(context)
        val time = labels.time(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()))
        val notification = Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.snoozed_until, time))
            .setContentText(alarm.label.ifBlank { null })
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .addAction(action(context, ActionReceiver.ACTION_CANCEL_SNOOZE, alarm.id, R.string.dismiss))
            .build()
        manager(context).notify(SNOOZED_ID_BASE + alarm.id, notification)
    }

    fun cancelSnoozed(context: Context, alarmId: Int) = manager(context).cancel(SNOOZED_ID_BASE + alarmId)

    fun showMissed(context: Context, alarm: Alarm) {
        val labels = AlarmLabels(context)
        val notification = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.missed_alarm, labels.time(alarm.hour, alarm.minute)))
            .setContentText(alarm.label.ifBlank { null })
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        manager(context).notify(MISSED_ID_BASE + alarm.id, notification)
    }

    private fun openApp(context: Context) =
        PendingIntent.getActivity(context, 0, MainActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)

    private fun action(context: Context, action: String, alarmId: Int, title: Int): Notification.Action {
        val intent = Intent(action).setClass(context, ActionReceiver::class.java).putExtra(ActionReceiver.EXTRA_ALARM_ID, alarmId)
        val pending = PendingIntent.getBroadcast(context, action.hashCode() + alarmId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), pending).build()
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
