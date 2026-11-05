package io.github.andreygladkov.inkclock.alarm

import android.content.Context
import android.icu.util.Calendar
import android.text.format.DateFormat
import io.github.andreygladkov.inkclock.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class AlarmLabels(private val context: Context, private val locale: Locale = context.resources.configuration.locales[0]) {
    val is24Hour = DateFormat.is24HourFormat(context)
    private val timeFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)

    // java.time takes the first day of the week from the locale's region, so a bare "ru" starts on Sunday;
    // ICU fills in the likely region (Russia) and starts on Monday, as people expect.
    val week: List<DayOfWeek> = Calendar.getInstance(locale).firstDayOfWeek.let { first ->
        val start = if (first == Calendar.SUNDAY) DayOfWeek.SUNDAY else DayOfWeek.of(first - 1)
        (0L until 7L).map { start.plus(it) }
    }

    fun time(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(timeFormat)

    fun time(at: ZonedDateTime): String = at.format(timeFormat)

    fun day(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }

    fun days(days: Set<DayOfWeek>): String = when (days) {
        emptySet<DayOfWeek>() -> context.getString(R.string.days_once)
        DayOfWeek.entries.toSet() -> context.getString(R.string.days_every)
        WEEKDAYS -> context.getString(R.string.days_weekdays)
        WEEKEND -> context.getString(R.string.days_weekend)
        else -> week.filter { it in days }.joinToString(", ", transform = ::day)
    }

    fun summary(alarm: Alarm): String = listOf(days(alarm.days), alarm.label).filter { it.isNotBlank() }.joinToString(" · ")

    fun next(next: NextAlarm, now: ZonedDateTime): String {
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), next.at.toLocalDate())
        val day = when (days) {
            0L -> context.getString(R.string.today)
            1L -> context.getString(R.string.tomorrow)
            else -> day(next.at.dayOfWeek)
        }
        return context.getString(R.string.next_alarm, "$day ${time(next.at)}", until(now, next.at))
    }

    fun until(now: ZonedDateTime, at: ZonedDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, at.plusSeconds(SECONDS_TO_ROUND_UP))
        val hours = minutes / 60
        return when {
            minutes < 1 -> context.getString(R.string.in_under_minute)
            hours == 0L -> context.getString(R.string.in_minutes, minutes)
            minutes % 60 == 0L -> context.getString(R.string.in_hours, hours)
            else -> context.getString(R.string.in_hours_minutes, hours, minutes % 60)
        }
    }

    private companion object {
        const val SECONDS_TO_ROUND_UP = 59L
        val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}
