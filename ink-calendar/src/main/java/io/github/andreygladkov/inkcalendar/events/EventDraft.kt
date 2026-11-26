package io.github.andreygladkov.inkcalendar.events

import java.time.LocalDateTime
import java.time.ZoneId

/** For an all-day event only the dates count, and [end]'s date is the last day the event takes. */
data class EventDraft(
    val title: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val calendarId: Long,
    val reminder: Int?,
) {
    val endsBeforeItStarts: Boolean get() = if (allDay) end.toLocalDate() < start.toLocalDate() else end < start

    companion object {
        val REMINDERS: List<Int?> = listOf(null, 0, 10, 60, 24 * 60)

        fun of(occurrence: Occurrence, reminder: Int?, zone: ZoneId) = EventDraft(
            title = occurrence.title,
            allDay = occurrence.allDay,
            start = occurrence.start(zone),
            end = if (occurrence.allDay) occurrence.lastDay(zone).atStartOfDay() else occurrence.finish(zone),
            calendarId = occurrence.calendarId,
            reminder = reminder,
        )
    }
}
