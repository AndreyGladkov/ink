package io.github.andreygladkov.inktasks.remind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.andreygladkov.inktasks.MainActivity
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.task.TaskActivity

/** Each reminder's notification is tagged with its key, so one whose task was done, changed or deleted can be found and taken back. */
object ReminderNotifications {
    private const val CHANNEL = "reminders"
    private const val ID = 1

    fun createChannel(context: Context) {
        manager(context).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH))
    }

    fun show(context: Context, reminder: Reminder) {
        val request = reminder.key.hashCode()
        val open = PendingIntent.getActivities(context, request, arrayOf(
            MainActivity.open(context, reminder.list).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            TaskActivity.edit(context, reminder.list, reminder.ref),
        ), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_check_box_outline)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentTitle(reminder.title)
            .setContentText(reminder.list)
            .setWhen(reminder.instant(TaskLists.clock.zone).toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(action(context, reminder, ReminderReceiver.ACTION_DONE, R.string.reminder_done, request))
            .addAction(action(context, reminder, ReminderReceiver.ACTION_LATER, R.string.reminder_later, request))
            .build()
        manager(context).notify(reminder.key, ID, notification)
    }

    fun canShow(context: Context): Boolean =
        manager(context).let { it.areNotificationsEnabled() && it.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE }

    fun keepOnly(context: Context, keys: Set<String>) {
        val manager = manager(context)
        manager.activeNotifications.filter { it.id == ID && it.tag !in keys }.forEach { manager.cancel(it.tag, ID) }
    }

    private fun action(context: Context, reminder: Reminder, action: String, title: Int, request: Int): Notification.Action {
        val pending = PendingIntent.getBroadcast(context, request, ReminderReceiver.intent(context, action, reminder),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), pending).build()
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
