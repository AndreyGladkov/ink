package io.github.andreygladkov.inkcalendar.events

import android.content.Context
import android.text.format.DateFormat
import io.github.andreygladkov.ink.ui.DatePatterns
import io.github.andreygladkov.inkcalendar.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventFormat(private val context: Context, private val zone: ZoneId = CalendarClock.zone) {
    private val locale = context.resources.configuration.locales[0]
    private val clock = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a", locale)
    private val longDay = DatePatterns.formatter(locale, "EEEEMMMMd")
    private val shortDay = DatePatterns.formatter(locale, "MMMMd")
    private val compactDay = DatePatterns.formatter(locale, "EEEMMMd")
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
    private val month = DateTimeFormatter.ofPattern("LLLL", locale)

    fun day(date: LocalDate): String = capitalized(longDay.format(date))

    fun compactDay(date: LocalDate): String = capitalized(compactDay.format(date))

    fun weekday(date: LocalDate): String = capitalized(weekday.format(date))

    fun month(date: LocalDate): String = capitalized(month.format(date))

    fun time(time: LocalTime): String = clock.format(time)

    fun timeOn(occurrence: Occurrence, date: LocalDate): String {
        if (occurrence.takesWholeDay(date, zone)) return context.getString(R.string.all_day)
        val start = occurrence.start(zone)
        val finish = occurrence.finish(zone)
        val startsToday = start.toLocalDate() == date
        val endsToday = finish.toLocalDate() == date || finish == date.plusDays(1).atStartOfDay()
        return when {
            !startsToday -> context.getString(R.string.time_until, time(finish.toLocalTime()))
            !endsToday -> context.getString(R.string.time_from, time(start.toLocalTime()))
            start == finish -> time(start.toLocalTime())
            else -> context.getString(R.string.time_range, time(start.toLocalTime()), time(finish.toLocalTime()))
        }
    }

    fun startOn(occurrence: Occurrence, date: LocalDate): String? {
        if (occurrence.takesWholeDay(date, zone)) return null
        val start = occurrence.start(zone)
        return if (start.toLocalDate() == date) time(start.toLocalTime()) else context.getString(R.string.time_until, time(occurrence.finish(zone).toLocalTime()))
    }

    fun span(occurrence: Occurrence): List<String> {
        val first = occurrence.firstDay(zone)
        val last = occurrence.lastDay(zone)
        if (occurrence.allDay) return listOf(if (first == last) day(first) else range(shortDay.format(first), shortDay.format(last)))
        val start = occurrence.start(zone)
        val finish = occurrence.finish(zone)
        if (first == last) return listOf(day(first), timeOn(occurrence, first))
        return listOf(range(moment(start), moment(finish)))
    }

    fun reminder(minutes: Int): String = when {
        minutes <= 0 -> context.getString(R.string.reminder_at_start)
        minutes % MINUTES_IN_DAY == 0 -> context.resources.getQuantityString(R.plurals.days_before, minutes / MINUTES_IN_DAY, minutes / MINUTES_IN_DAY)
        minutes % MINUTES_IN_HOUR == 0 -> context.resources.getQuantityString(R.plurals.hours_before, minutes / MINUTES_IN_HOUR, minutes / MINUTES_IN_HOUR)
        else -> context.resources.getQuantityString(R.plurals.minutes_before, minutes, minutes)
    }

    private fun moment(time: LocalDateTime) = "${shortDay.format(time)}, ${time(time.toLocalTime())}"

    private fun range(from: String, to: String) = "$from – $to"

    private fun capitalized(text: String) = text.replaceFirstChar { it.titlecase(locale) }

    private companion object {
        const val MINUTES_IN_HOUR = 60
        const val MINUTES_IN_DAY = 24 * 60
    }
}
