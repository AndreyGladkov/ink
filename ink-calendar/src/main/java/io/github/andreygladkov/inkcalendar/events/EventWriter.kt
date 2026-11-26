package io.github.andreygladkov.inkcalendar.events

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import java.time.ZoneId
import java.time.ZoneOffset

class EventWriter(private val resolver: ContentResolver, private val zone: ZoneId) {
    /** Returns the new event's id, or null if the provider refused it. */
    fun insert(draft: EventDraft): Long? {
        val uri = resolver.insert(Events.CONTENT_URI, values(draft)) ?: return null
        val eventId = ContentUris.parseId(uri)
        draft.reminder?.let { addReminder(eventId, it) }
        return eventId
    }

    /** A synced event may carry several reminders; the form shows one, so they are replaced only when it was changed. */
    fun update(eventId: Long, draft: EventDraft, reminderChanged: Boolean) {
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values(draft), null, null)
        if (!reminderChanged) return
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
        draft.reminder?.let { addReminder(eventId, it) }
    }

    fun delete(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    /** A calendar on the phone alone, for a phone with no account calendar to write to. */
    fun createLocalCalendar(name: String): Long? {
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, name)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        return resolver.insert(uri, ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, name)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, name)
            put(Calendars.CALENDAR_DISPLAY_NAME, name)
            put(Calendars.CALENDAR_COLOR, BLACK)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, name)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, zone.id)
        })?.let(ContentUris::parseId)
    }

    private fun addReminder(eventId: Long, minutes: Int) {
        resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
            put(Reminders.EVENT_ID, eventId)
            put(Reminders.MINUTES, minutes)
            put(Reminders.METHOD, Reminders.METHOD_ALERT)
        })
    }

    /** The provider wants an all-day event at midnight UTC with an exclusive end. */
    private fun values(draft: EventDraft) = ContentValues().apply {
        put(Events.CALENDAR_ID, draft.calendarId)
        put(Events.TITLE, draft.title)
        put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
        put(Events.HAS_ALARM, if (draft.reminder != null) 1 else 0)
        if (draft.allDay) {
            put(Events.DTSTART, draft.start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(Events.DTEND, draft.end.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            put(Events.EVENT_TIMEZONE, UTC)
        } else {
            put(Events.DTSTART, draft.start.atZone(zone).toInstant().toEpochMilli())
            put(Events.DTEND, draft.end.atZone(zone).toInstant().toEpochMilli())
            put(Events.EVENT_TIMEZONE, zone.id)
        }
    }

    companion object {
        private const val PREF_DEFAULT_CALENDAR = "default_calendar"
        private const val BLACK = 0xFF000000.toInt()
        private const val UTC = "UTC"

        fun of(context: Context) = EventWriter(context.contentResolver, CalendarClock.zone)

        fun defaultCalendar(context: Context, writable: List<CalendarInfo>): CalendarInfo? {
            val chosen = context.inkPrefs().getLong(PREF_DEFAULT_CALENDAR, -1)
            val hidden = EventStore.hidden(context)
            return writable.firstOrNull { it.id == chosen } ?: writable.firstOrNull { it.id !in hidden } ?: writable.firstOrNull()
        }

        fun setDefaultCalendar(context: Context, calendarId: Long) = context.inkPrefs().edit { putLong(PREF_DEFAULT_CALENDAR, calendarId) }
    }
}
