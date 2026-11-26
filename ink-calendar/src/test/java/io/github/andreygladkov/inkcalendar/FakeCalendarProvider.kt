package io.github.andreygladkov.inkcalendar

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import org.robolectric.Robolectric

/** Robolectric has no calendar provider; this one keeps rows in memory and answers the queries the app makes. */
class FakeCalendarProvider : ContentProvider() {
    val calendars = mutableListOf<Map<String, Any?>>()
    val instances = mutableListOf<Map<String, Any?>>()
    val reminders = mutableListOf<Map<String, Any?>>()
    val alerts = mutableListOf<Map<String, Any?>>()
    val events = mutableMapOf<Long, ContentValues>()
    val deletedEvents = mutableListOf<Long>()
    private var nextId = 100L

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val segments = uri.pathSegments
        val rows = when (segments.first()) {
            "calendars" -> calendars
            "instances" -> {
                val begin = segments[2].toLong()
                val end = segments[3].toLong()
                instances.filter { (it[CalendarContract.Instances.BEGIN] as Long) < end && (it[CalendarContract.Instances.END] as Long) > begin }
            }
            "reminders" -> reminders.filter { it[CalendarContract.Reminders.EVENT_ID].toString() == selectionArgs?.firstOrNull() }
            "calendar_alerts" -> alerts.filter { it[CalendarContract.CalendarAlerts.ALARM_TIME].toString() == selectionArgs?.firstOrNull() }
            else -> error("unexpected query $uri")
        }
        val columns = projection ?: rows.firstOrNull()?.keys?.toTypedArray() ?: emptyArray()
        return MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val id = nextId++
        val row = values ?: ContentValues()
        when (uri.pathSegments.first()) {
            "events" -> events[id] = row
            "reminders" -> reminders += row.keySet().associateWith { row.get(it) }
            "calendars" -> {
                check(uri.getQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER) == "true") { "only a sync adapter may add a calendar" }
                calendars += row.keySet().associateWith { row.get(it) } + (CalendarContract.Calendars._ID to id)
            }
            else -> error("unexpected insert $uri")
        }
        return ContentUris.withAppendedId(uri, id)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = when (uri.pathSegments.first()) {
        "events" -> {
            deletedEvents += ContentUris.parseId(uri)
            1
        }
        "reminders" -> {
            val before = reminders.size
            reminders.removeAll { it[CalendarContract.Reminders.EVENT_ID].toString() == selectionArgs?.firstOrNull() }
            before - reminders.size
        }
        else -> error("unexpected delete $uri")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        check(uri.pathSegments.first() == "events") { "unexpected update $uri" }
        events[ContentUris.parseId(uri)] = values ?: ContentValues()
        return 1
    }

    fun calendar(id: Long, name: String, account: String = "me@example.org", visible: Boolean = true, accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_OWNER) {
        calendars += mapOf(
            CalendarContract.Calendars._ID to id,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to name,
            CalendarContract.Calendars.VISIBLE to if (visible) 1 else 0,
            CalendarContract.Calendars.ACCOUNT_NAME to account,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL to accessLevel,
        )
    }

    fun instance(
        eventId: Long,
        begin: Long,
        end: Long,
        title: String,
        allDay: Boolean = false,
        calendarId: Long = 1,
        location: String? = null,
        notes: String? = null,
        rrule: String? = null,
    ) {
        val calendar = calendars.firstOrNull { it[CalendarContract.Calendars._ID] == calendarId }
        instances += mapOf(
            CalendarContract.Instances.EVENT_ID to eventId,
            CalendarContract.Instances.BEGIN to begin,
            CalendarContract.Instances.END to end,
            CalendarContract.Instances.ALL_DAY to if (allDay) 1 else 0,
            CalendarContract.Instances.TITLE to title,
            CalendarContract.Instances.CALENDAR_ID to calendarId,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME to calendar?.get(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
            CalendarContract.Instances.EVENT_LOCATION to location,
            CalendarContract.Instances.DESCRIPTION to notes,
            CalendarContract.Instances.RRULE to rrule,
            CalendarContract.Instances.VISIBLE to (calendar?.get(CalendarContract.Calendars.VISIBLE) ?: 1),
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL to (calendar?.get(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL) ?: CalendarContract.Calendars.CAL_ACCESS_OWNER),
        )
    }

    fun reminder(eventId: Long, minutes: Int, method: Int = CalendarContract.Reminders.METHOD_ALERT) {
        reminders += mapOf(
            CalendarContract.Reminders.EVENT_ID to eventId,
            CalendarContract.Reminders.MINUTES to minutes,
            CalendarContract.Reminders.METHOD to method,
        )
    }

    fun alert(alarmTime: Long, eventId: Long, begin: Long, end: Long, title: String, calendarId: Long = 1, location: String? = null) {
        alerts += mapOf(
            CalendarContract.CalendarAlerts.ALARM_TIME to alarmTime,
            CalendarContract.CalendarAlerts.EVENT_ID to eventId,
            CalendarContract.CalendarAlerts.BEGIN to begin,
            CalendarContract.CalendarAlerts.END to end,
            CalendarContract.CalendarAlerts.ALL_DAY to 0,
            CalendarContract.CalendarAlerts.TITLE to title,
            CalendarContract.CalendarAlerts.EVENT_LOCATION to location,
            CalendarContract.CalendarAlerts.CALENDAR_ID to calendarId,
        )
    }

    companion object {
        fun install(): FakeCalendarProvider = Robolectric.setupContentProvider(FakeCalendarProvider::class.java, CalendarContract.AUTHORITY)
    }
}
