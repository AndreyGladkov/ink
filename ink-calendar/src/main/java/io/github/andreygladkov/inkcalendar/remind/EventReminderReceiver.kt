package io.github.andreygladkov.inkcalendar.remind

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.github.andreygladkov.inkcalendar.events.EventStore

/** Exported because the calendar provider, not the system, sends the reminder; a forged one finds no alert to show. */
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER || !Reminders.isOn(context)) return
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return
        val alarmTime = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        Reminders.due(context.contentResolver, alarmTime, EventStore.hidden(context)).forEach { ReminderNotifications.show(context, it) }
    }
}
