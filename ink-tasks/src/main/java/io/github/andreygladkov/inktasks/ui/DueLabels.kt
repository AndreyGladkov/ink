package io.github.andreygladkov.inktasks.ui

import android.content.Context
import io.github.andreygladkov.ink.ui.DatePatterns
import io.github.andreygladkov.inktasks.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DueLabels(private val context: Context, val today: LocalDate) {
    private val locale = context.resources.configuration.locales[0]
    private val thisYear = DatePatterns.formatter(locale, "dMMM")
    private val otherYear = DatePatterns.formatter(locale, "dMMMyyyy")
    private val timeOfDay = DatePatterns.time(context, locale)

    fun day(date: LocalDate): String = relative(date) ?: dated(date)

    fun dayInSentence(date: LocalDate): String = relative(date)?.replaceFirstChar { it.lowercase(locale) } ?: dated(date)

    private fun relative(date: LocalDate): String? = when (date) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        today.minusDays(1) -> context.getString(R.string.yesterday)
        else -> null
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
