package io.github.andreygladkov.inkcalendar.events

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EventStore(private val resolver: ContentResolver, private val hidden: Set<Long>) {
    fun calendars(): List<CalendarInfo> =
        resolver.query(Calendars.CONTENT_URI, CALENDAR_COLUMNS, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(2) == 0) continue
                    add(CalendarInfo(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(3).orEmpty(),
                        cursor.getInt(4) >= Calendars.CAL_ACCESS_CONTRIBUTOR))
                }
            }
        }.orEmpty().sortedWith(compareBy({ it.account }, { it.name }))

    /** Events of hidden calendars and of calendars the phone hides are left out; [until] is exclusive. */
    fun occurrences(from: LocalDate, until: LocalDate, zone: ZoneId): List<Occurrence> {
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = until.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        return resolver.query(uri, INSTANCE_COLUMNS, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getInt(INSTANCE_VISIBLE) == 0) continue
                    val occurrence = occurrence(cursor)
                    if (occurrence.calendarId in hidden) continue
                    if (occurrence.lastDay(zone) >= from && occurrence.firstDay(zone) < until) add(occurrence)
                }
            }
        }.orEmpty()
    }

    fun occurrence(eventId: Long, begin: Long, zone: ZoneId): Occurrence? {
        val day = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        return occurrences(day.minusDays(1), day.plusDays(2), zone).firstOrNull { it.eventId == eventId && it.begin == begin }
    }

    fun reminders(eventId: Long): List<Int> =
        Reminders.query(resolver, eventId, arrayOf(Reminders.MINUTES, Reminders.METHOD))?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val method = cursor.getInt(1)
                    if (method == Reminders.METHOD_ALERT || method == Reminders.METHOD_DEFAULT) add(cursor.getInt(0))
                }
            }
        }.orEmpty().distinct().sorted()

    private fun occurrence(cursor: Cursor) = Occurrence(
        eventId = cursor.getLong(0),
        begin = cursor.getLong(1),
        end = cursor.getLong(2),
        allDay = cursor.getInt(3) != 0,
        title = cursor.getString(4).orEmpty(),
        calendarId = cursor.getLong(5),
        calendarName = cursor.getString(6).orEmpty(),
        location = cursor.getString(7).orEmpty(),
        notes = cursor.getString(8).orEmpty(),
        repeats = !cursor.getString(9).isNullOrEmpty(),
    )

    companion object {
        private const val PREF_HIDDEN = "hidden_calendars"
        private const val INSTANCE_VISIBLE = 10
        private val CALENDAR_COLUMNS = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.VISIBLE, Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_ACCESS_LEVEL)
        private val INSTANCE_COLUMNS = arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.TITLE,
            Instances.CALENDAR_ID, Instances.CALENDAR_DISPLAY_NAME, Instances.EVENT_LOCATION, Instances.DESCRIPTION, Instances.RRULE,
            Instances.VISIBLE)

        fun of(context: Context) = EventStore(context.contentResolver, hidden(context))

        fun hidden(context: Context): Set<Long> =
            context.inkPrefs().getStringSet(PREF_HIDDEN, emptySet()).orEmpty().mapNotNull(String::toLongOrNull).toSet()

        fun setShown(context: Context, calendarId: Long, shown: Boolean) {
            val hidden = hidden(context).let { if (shown) it - calendarId else it + calendarId }
            context.inkPrefs().edit { putStringSet(PREF_HIDDEN, hidden.map(Long::toString).toSet()) }
        }
    }
}
