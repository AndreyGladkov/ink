package io.github.andreygladkov.inkcalendar.events

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class Occurrence(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val calendarId: Long,
    val calendarName: String,
    val location: String,
    val notes: String,
    val repeats: Boolean,
) {
    /** The provider keeps all-day events at midnight UTC, whatever the phone's zone. */
    private fun zone(local: ZoneId): ZoneId = if (allDay) ZoneOffset.UTC else local

    fun start(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone(zone))

    fun finish(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), zone(zone))

    fun firstDay(zone: ZoneId): LocalDate = start(zone).toLocalDate()

    /** The end is exclusive, so an event ending at midnight belongs to the day before. */
    fun lastDay(zone: ZoneId): LocalDate {
        val finish = finish(zone)
        val last = if (finish.toLocalTime() == LocalTime.MIDNIGHT) finish.toLocalDate().minusDays(1) else finish.toLocalDate()
        return maxOf(last, firstDay(zone))
    }

    fun occursOn(date: LocalDate, zone: ZoneId): Boolean = date in firstDay(zone)..lastDay(zone)

    fun days(zone: ZoneId): Sequence<LocalDate> = generateSequence(firstDay(zone)) { it.plusDays(1) }.takeWhile { it <= lastDay(zone) }

    fun takesWholeDay(date: LocalDate, zone: ZoneId): Boolean =
        allDay || (start(zone) <= date.atStartOfDay() && finish(zone) >= date.plusDays(1).atStartOfDay())

    companion object {
        fun onDay(occurrences: List<Occurrence>, date: LocalDate, zone: ZoneId): List<Occurrence> =
            occurrences.filter { it.occursOn(date, zone) }
                .sortedWith(compareBy<Occurrence> { !it.takesWholeDay(date, zone) }.thenBy { it.start(zone) }.thenBy { it.title })
    }
}
