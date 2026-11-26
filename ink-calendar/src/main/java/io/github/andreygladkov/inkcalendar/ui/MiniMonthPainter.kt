package io.github.andreygladkov.inkcalendar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.ui.MonthGrid
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.min

class MiniMonthPainter(private val context: Context, private val style: InkStyle = InkStyle.of(context)) {
    private val locale get() = context.resources.configuration.locales[0]
    private val text = TextPaint().apply {
        isAntiAlias = false
        textAlign = Paint.Align.CENTER
    }
    private val fill = Paint().apply { isAntiAlias = false }

    fun draw(canvas: Canvas, month: YearMonth, today: LocalDate, area: Rect, name: String?, weekdays: Boolean = false) {
        var top = area.top.toFloat()
        if (name != null) {
            use(area.height() * NAME_SHARE, InkStyle.BOLD_WEIGHT)
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(name, area.left.toFloat(), top - text.ascent(), text)
            text.textAlign = Paint.Align.CENTER
            top += (text.descent() - text.ascent()) * NAME_GAP
        }
        val cellWidth = area.width() / MonthGrid.DAYS_IN_WEEK.toFloat()
        val cellHeight = (area.bottom - top) / (MonthGrid.WEEKS + if (weekdays) 1 else 0)
        use(min(cellHeight * DIGIT_TO_CELL, cellWidth * DIGIT_TO_WIDTH), style.weight)
        val firstDay = MonthGrid.firstDayOfWeek(locale)
        if (weekdays) {
            repeat(MonthGrid.DAYS_IN_WEEK) { index ->
                val name = firstDay.plus(index.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale)
                canvas.drawText(name, area.left + (index + 0.5f) * cellWidth, top + cellHeight / 2 - (text.ascent() + text.descent()) / 2, text)
            }
            top += cellHeight
        }
        val first = MonthGrid.firstShown(month, firstDay)
        repeat(MonthGrid.WEEKS * MonthGrid.DAYS_IN_WEEK) { index ->
            val date = first.plusDays(index.toLong())
            if (YearMonth.from(date) != month) return@repeat
            val centerX = area.left + (index % MonthGrid.DAYS_IN_WEEK + 0.5f) * cellWidth
            val centerY = top + (index / MonthGrid.DAYS_IN_WEEK + 0.5f) * cellHeight
            val isToday = date == today
            if (isToday) {
                fill.color = style.foreground
                canvas.drawCircle(centerX, centerY, min(cellWidth, cellHeight) / 2, fill)
            }
            text.color = if (isToday) style.background else style.foreground
            canvas.drawText(date.dayOfMonth.toString(), centerX, centerY - (text.ascent() + text.descent()) / 2, text)
        }
    }

    private fun use(size: Float, weight: Int) {
        text.textSize = size
        text.typeface = StemFit.typeface(size, weight)
        text.color = style.foreground
    }

    private companion object {
        const val NAME_SHARE = 0.11f
        const val NAME_GAP = 1.15f
        const val DIGIT_TO_CELL = 0.6f
        const val DIGIT_TO_WIDTH = 0.55f
    }
}
