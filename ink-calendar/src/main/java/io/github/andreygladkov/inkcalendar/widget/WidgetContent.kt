package io.github.andreygladkov.inkcalendar.widget

import android.content.Context
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.Occurrence
import io.github.andreygladkov.inkcalendar.tasks.DueTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WidgetContent(
    val today: LocalDate,
    val weekday: String,
    val day: String,
    val month: String,
    val todayEntries: List<Entry>,
    val tomorrowEntries: List<Entry>,
    val tomorrowLabel: String,
    val note: String?,
) {
    /** [time] is the start, or how the event runs into the day; an event taking the whole day has none. */
    data class Entry(val time: String?, val title: String, val isTask: Boolean = false)

    companion object {
        /** [occurrences] is null without access to the calendar. */
        fun of(context: Context, now: Instant, zone: ZoneId, occurrences: List<Occurrence>?, tasks: List<DueTask> = emptyList(),
            format: EventFormat = EventFormat(context, zone)): WidgetContent {
            val today = now.atZone(zone).toLocalDate()
            val tomorrow = today.plusDays(1)
            val left = occurrences.orEmpty().filter { it.end > now.toEpochMilli() || it.allDay }
            fun entries(date: LocalDate) = Occurrence.onDay(left, date, zone).map {
                Entry(format.startOn(it, date), it.title.ifBlank { context.getString(R.string.no_title) })
            } + DueTask.onDay(tasks, date).map {
                Entry(it.time?.let(format::time), it.title.ifBlank { context.getString(R.string.no_title) }, isTask = true)
            }
            val todayEntries = entries(today)
            val tomorrowEntries = entries(tomorrow)
            val note = when {
                occurrences == null -> context.getString(R.string.widget_no_access)
                todayEntries.isEmpty() && tomorrowEntries.isEmpty() -> context.getString(R.string.no_events)
                else -> null
            }
            return WidgetContent(today, format.weekday(today), today.dayOfMonth.toString(), format.month(today),
                todayEntries, tomorrowEntries, context.getString(R.string.tomorrow), note)
        }

        /** Shown events drop off as they end, and the date turns at midnight. */
        fun nextChange(now: Instant, zone: ZoneId, occurrences: List<Occurrence>?): Instant {
            val midnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val ends = occurrences.orEmpty().filter { !it.allDay }.map { Instant.ofEpochMilli(it.end) }.filter { it > now }
            return (ends + midnight).min()
        }
    }
}
