package io.github.andreygladkov.inkcalendar.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.events.Occurrence

class ReminderActions : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrence = occurrence(intent) ?: return
        when (intent.action) {
            ACTION_DISMISS -> ReminderNotifications.cancel(context, occurrence)
            ACTION_SNOOZE -> {
                ReminderNotifications.cancel(context, occurrence)
                snooze(context, occurrence)
            }
            ACTION_SHOW -> if (Reminders.isOn(context)) ReminderNotifications.show(context, occurrence)
        }
    }

    private fun snooze(context: Context, occurrence: Occurrence) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = CalendarClock.now().toEpochMilli() + SNOOZE_MS
        val show = PendingIntent.getBroadcast(context, "${occurrence.eventId}:${occurrence.begin}".hashCode(), intent(context, ACTION_SHOW, occurrence),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (AlarmManagerCompat.canScheduleExactAlarms(alarms)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, show)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, show)
    }

    companion object {
        internal const val ACTION_SNOOZE = "io.github.andreygladkov.inkcalendar.action.SNOOZE"
        internal const val ACTION_DISMISS = "io.github.andreygladkov.inkcalendar.action.DISMISS"
        internal const val ACTION_SHOW = "io.github.andreygladkov.inkcalendar.action.SHOW"
        private const val SNOOZE_MS = 10 * 60 * 1000L
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_END = "end"
        private const val EXTRA_ALL_DAY = "all_day"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"

        fun intent(context: Context, action: String, occurrence: Occurrence): Intent = Intent(context, ReminderActions::class.java)
            .setAction(action)
            .putExtra(EXTRA_EVENT, occurrence.eventId)
            .putExtra(EXTRA_BEGIN, occurrence.begin)
            .putExtra(EXTRA_END, occurrence.end)
            .putExtra(EXTRA_ALL_DAY, occurrence.allDay)
            .putExtra(EXTRA_TITLE, occurrence.title)
            .putExtra(EXTRA_LOCATION, occurrence.location)

        private fun occurrence(intent: Intent): Occurrence? {
            if (!intent.hasExtra(EXTRA_EVENT)) return null
            return Occurrence(intent.getLongExtra(EXTRA_EVENT, 0), intent.getLongExtra(EXTRA_BEGIN, 0), intent.getLongExtra(EXTRA_END, 0),
                intent.getBooleanExtra(EXTRA_ALL_DAY, false), intent.getStringExtra(EXTRA_TITLE).orEmpty(), 0, "",
                intent.getStringExtra(EXTRA_LOCATION).orEmpty(), "", repeats = false)
        }
    }
}
