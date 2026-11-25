package io.github.andreygladkov.inktasks.ui

import android.content.Context
import android.text.format.DateFormat
import io.github.andreygladkov.inktasks.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class DueLabels(private val context: Context, val today: LocalDate) {
    private val locale = context.resources.configuration.locales[0]
    private val thisYear = DateTimeFormatter.ofPattern("d MMM", locale)
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val timeOfDay = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"), locale)

    fun day(date: LocalDate): String = when (date) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> dated(date)
    }

    fun withDate(date: LocalDate): String {
        val day = day(date)
        return if (day == dated(date)) day else "$day, ${dated(date)}"
    }

    fun time(time: LocalTime): String = time.format(timeOfDay)

    fun reminder(at: LocalDateTime, due: LocalDate?): String = if (at.toLocalDate() == due) {
        context.getString(R.string.reminder_at, time(at.toLocalTime()))
    } else {
        "${withDate(at.toLocalDate())}, ${time(at.toLocalTime())}"
    }

    private fun dated(date: LocalDate): String = date.format(if (date.year == today.year) thisYear else otherYear)

    fun isOverdue(date: LocalDate) = date.isBefore(today)
}
