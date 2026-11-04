package io.github.andreygladkov.inkmessages

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MessageDates(
    private val today: String,
    private val yesterday: String,
    private val yesterdayShort: String,
    private val monthsShort: List<String>,
    private val monthsFull: List<String>,
    private val dayMonth: String,
    private val dayMonthYear: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun listLabel(date: Long, now: Long): String {
        val day = dayOf(date)
        val current = dayOf(now)
        return when {
            day == current -> time(date)
            day == current.minusDays(1) -> yesterdayShort
            day.year == current.year -> dayMonth.format(day.dayOfMonth, monthsShort[day.monthValue - 1])
            else -> day.format(SHORT_DATE)
        }
    }

    fun dayHeader(day: LocalDate, now: Long): String {
        val current = dayOf(now)
        return when {
            day == current -> today
            day == current.minusDays(1) -> yesterday
            day.year == current.year -> dayMonth.format(day.dayOfMonth, monthsFull[day.monthValue - 1])
            else -> dayMonthYear.format(day.dayOfMonth, monthsFull[day.monthValue - 1], day.year)
        }
    }

    fun time(date: Long): String = Instant.ofEpochMilli(date).atZone(zone).format(TIME)

    fun dayOf(date: Long): LocalDate = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy")

        fun of(context: Context) = MessageDates(
            today = context.getString(R.string.date_today),
            yesterday = context.getString(R.string.date_yesterday),
            yesterdayShort = context.getString(R.string.date_yesterday_short),
            monthsShort = context.resources.getStringArray(R.array.months_short).toList(),
            monthsFull = context.resources.getStringArray(R.array.months_full).toList(),
            dayMonth = context.getString(R.string.date_day_month),
            dayMonthYear = context.getString(R.string.date_day_month_year),
        )
    }
}
