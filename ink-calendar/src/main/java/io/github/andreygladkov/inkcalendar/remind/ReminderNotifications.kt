package io.github.andreygladkov.inkcalendar.remind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inkcalendar.MainActivity
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.event.EventActivity
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.Occurrence

object ReminderNotifications {
    private const val CHANNEL = "reminders"
    private const val ID = 1

    fun createChannel(context: Context) {
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH))
    }

    fun show(context: Context, occurrence: Occurrence) {
        val tag = tag(occurrence)
        val request = tag.hashCode()
        val open = PendingIntent.getActivities(context, request, arrayOf(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            EventActivity.intent(context, occurrence.eventId, occurrence.begin),
        ), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = (EventFormat(context).span(occurrence) + occurrence.location).filter { it.isNotBlank() }.joinToString(", ")
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_EVENT)
            .setContentTitle(occurrence.title.ifBlank { context.getString(R.string.no_title) })
            .setContentText(text)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(action(context, occurrence, ReminderActions.ACTION_SNOOZE, R.string.snooze, request))
            .addAction(action(context, occurrence, ReminderActions.ACTION_DISMISS, R.string.dismiss, request))
            .build()
        manager(context).notify(tag, ID, notification)
    }

    fun cancel(context: Context, occurrence: Occurrence) = manager(context).cancel(tag(occurrence), ID)

    private fun tag(occurrence: Occurrence) = "${occurrence.eventId}:${occurrence.begin}"

    private fun action(context: Context, occurrence: Occurrence, action: String, title: Int, request: Int): Notification.Action {
        val pending = PendingIntent.getBroadcast(context, request, ReminderActions.intent(context, action, occurrence),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), pending).build()
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
