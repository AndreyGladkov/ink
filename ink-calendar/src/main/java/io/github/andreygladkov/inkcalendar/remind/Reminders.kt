package io.github.andreygladkov.inkcalendar.remind

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract.CalendarAlerts
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkcalendar.events.Occurrence

object Reminders {
    private const val PREF_ON = "reminders_on"
    private val COLUMNS = arrayOf(CalendarAlerts.EVENT_ID, CalendarAlerts.BEGIN, CalendarAlerts.END, CalendarAlerts.ALL_DAY, CalendarAlerts.TITLE,
        CalendarAlerts.EVENT_LOCATION, CalendarAlerts.CALENDAR_ID)

    fun isOn(context: Context): Boolean = context.inkPrefs().getBoolean(PREF_ON, true)

    fun setOn(context: Context, on: Boolean) = context.inkPrefs().edit { putBoolean(PREF_ON, on) }

    /** The provider broadcasts only the alarm time; the events it is for are the alerts set for that time. */
    fun due(resolver: ContentResolver, alarmTime: Long, hidden: Set<Long>): List<Occurrence> =
        resolver.query(CalendarAlerts.CONTENT_URI, COLUMNS, "${CalendarAlerts.ALARM_TIME}=?", arrayOf(alarmTime.toString()), null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getLong(6) in hidden) continue
                    add(Occurrence(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) != 0, cursor.getString(4).orEmpty(),
                        cursor.getLong(6), "", cursor.getString(5).orEmpty(), "", repeats = false))
                }
            }
        }.orEmpty().distinctBy { it.eventId to it.begin }
}
