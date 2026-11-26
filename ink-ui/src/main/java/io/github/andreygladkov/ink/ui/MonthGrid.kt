package io.github.andreygladkov.ink.ui

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Always six weeks tall, so what sits under the grid stays put from month to month. */
class MonthGrid(context: Context) : LinearLayout(context) {
    private val locale get() = resources.configuration.locales[0]
    private val cells = mutableListOf<DayCell>()

    val days: List<DayCell> get() = cells
    var onDay: ((LocalDate) -> Unit)? = null

    init {
        orientation = VERTICAL
        addView(weekdayRow(), wide())
        repeat(WEEKS) { addView(weekRow(), wide()) }
    }

    fun show(month: YearMonth, today: LocalDate, selected: LocalDate?, marked: Set<LocalDate> = emptySet()) {
        val start = firstShown(month, firstDayOfWeek(locale))
        cells.forEachIndexed { index, cell ->
            val date = start.plusDays(index.toLong())
            cell.visibility = if (YearMonth.from(date) == month) View.VISIBLE else View.INVISIBLE
            cell.text = String.format(locale, "%d", date.dayOfMonth)
            cell.isToday = date == today
            cell.isSelected = date == selected
            cell.isMarked = date in marked
            cell.contentDescription = date.toString()
            cell.setOnClickListener { onDay?.invoke(date) }
        }
    }

    private fun weekdayRow() = LinearLayout(context).apply {
        val first = firstDayOfWeek(locale)
        val gap = (GAP_DP * resources.displayMetrics.density).roundToInt()
        repeat(DAYS_IN_WEEK) { index ->
            addView(InkTextView(context).apply {
                text = first.plus(index.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                textScale = InkStyle.SECONDARY_SCALE
                gravity = Gravity.CENTER
                setPadding(0, gap, 0, gap)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun weekRow() = LinearLayout(context).apply {
        repeat(DAYS_IN_WEEK) {
            val cell = DayCell(context)
            cells += cell
            addView(cell, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    companion object {
        const val WEEKS = 6
        const val DAYS_IN_WEEK = 7
        private const val GAP_DP = 8f

        /** A language set without a country ("ru") would start weeks on Sunday; its likely country decides instead. */
        fun firstDayOfWeek(locale: Locale): DayOfWeek =
            DayOfWeek.SUNDAY.plus(Calendar.getInstance(ULocale.addLikelySubtags(ULocale.forLocale(locale))).firstDayOfWeek - 1L)

        fun firstShown(month: YearMonth, firstDay: DayOfWeek): LocalDate =
            month.atDay(1).let { it.minusDays(((it.dayOfWeek.value - firstDay.value + DAYS_IN_WEEK) % DAYS_IN_WEEK).toLong()) }

        fun title(month: YearMonth, locale: Locale): String =
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
    }
}
