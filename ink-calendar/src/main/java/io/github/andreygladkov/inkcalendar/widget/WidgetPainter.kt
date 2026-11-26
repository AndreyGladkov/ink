package io.github.andreygladkov.inkcalendar.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inkcalendar.ui.MiniMonthPainter
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * RemoteViews text is always antialiased and the widget host decides the colors, so the whole widget is drawn
 * here as one two-tone picture.
 */
class WidgetPainter(private val context: Context, private val style: InkStyle = InkStyle.of(context)) {
    private val metrics = context.resources.displayMetrics
    private val paint = TextPaint().also {
        it.isAntiAlias = false
        it.color = style.foreground
    }

    private data class Line(val text: String, val scale: Float, val bold: Boolean = false)

    fun draw(content: WidgetContent, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(style.background)
        val area = Rect(0, 0, width, height).apply { inset(dp(PADDING_DP), dp(PADDING_DP)) }
        if (width / metrics.density < SMALL_DP) {
            drawSmall(canvas, content, area)
            return bitmap
        }
        val events = if (height / metrics.density >= TALL_DP) {
            val month = Rect(area.left, area.bottom - min(area.height() / 2, area.width() * MONTH_HEIGHT_PERCENT / 100), area.right, area.bottom)
            MiniMonthPainter(context, style).draw(canvas, YearMonth.from(content.today), content.today, month, name = null, weekdays = true)
            Rect(area.left, area.top, area.right, month.top - dp(GAP_DP))
        } else {
            area
        }
        val date = dateLines(content, events.height())
        val dateWidth = min(events.width() * DATE_SHARE_PERCENT / 100, date.maxOf(::width))
        drawLines(canvas, date, events.left, events.top, dateWidth)
        drawEvents(canvas, content, Rect(events.left + dateWidth + dp(COLUMN_GAP_DP), events.top, events.right, events.bottom))
        return bitmap
    }

    private fun drawSmall(canvas: Canvas, content: WidgetContent, area: Rect) = drawLines(canvas, dateLines(content, area.height()), area.left, area.top, area.width())

    /** The weekday stays as long as it fits, and the day number shrinks to make room for it. */
    private fun dateLines(content: WidgetContent, height: Int): List<Line> {
        val weekday = Line(content.weekday, InkStyle.SECONDARY_SCALE, bold = true)
        val month = Line(content.month, InkStyle.SECONDARY_SCALE)
        val unit = height(Line("", 1f)).toFloat()
        fun day(room: Int) = Line(content.day, (room / unit).coerceAtMost(DAY_SCALE), bold = true)
        val full = listOf(weekday, day(height - height(weekday) - height(month)), month)
        if (full[1].scale >= DAY_SCALE) return full
        val withWeekday = listOf(weekday, day(height - height(weekday)))
        return if (withWeekday[1].scale >= MIN_DAY_SCALE) withWeekday else listOf(day(height))
    }

    private fun drawLines(canvas: Canvas, lines: List<Line>, left: Int, top: Int, width: Int) {
        var y = top
        lines.forEach { line ->
            drawLine(canvas, line, left, y, width)
            y += height(line)
        }
    }

    private fun drawEvents(canvas: Canvas, content: WidgetContent, area: Rect) {
        val rows = content.note?.let { listOf(Line(it, InkStyle.SECONDARY_SCALE) to null) }
            ?: (content.todayEntries.map(::entry) +
                (if (content.tomorrowEntries.isEmpty()) emptyList() else listOf(Line(content.tomorrowLabel, InkStyle.SECONDARY_SCALE, bold = true) to null)) +
                content.tomorrowEntries.map(::entry))
        val lineHeight = height(Line("", InkStyle.SECONDARY_SCALE))
        var top = area.top
        for ((first, title) in rows) {
            if (top + lineHeight > area.bottom) break
            if (title == null) {
                drawLine(canvas, first, area.left, top, area.width())
            } else {
                val lead = if (first.text.isEmpty()) 0 else min(width(first) + dp(GAP_DP), area.width() / 2)
                drawLine(canvas, first, area.left, top, lead)
                drawLine(canvas, title, area.left + lead, top, area.width() - lead)
            }
            top += lineHeight
        }
    }

    private fun entry(entry: WidgetContent.Entry) =
        Line(entry.time.orEmpty(), InkStyle.SECONDARY_SCALE, bold = true) to Line(entry.title, InkStyle.SECONDARY_SCALE)

    private fun drawLine(canvas: Canvas, line: Line, left: Int, top: Int, width: Int) {
        if (width <= 0) return
        use(line)
        val text = TextUtils.ellipsize(line.text, paint, width.toFloat(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(text, left.toFloat(), (top - paint.ascent()).roundToInt().toFloat(), paint)
    }

    private fun width(line: Line): Int {
        use(line)
        return ceil(paint.measureText(line.text)).toInt()
    }

    private fun height(lines: List<Line>): Int = lines.sumOf(::height)

    private fun height(line: Line): Int {
        use(line)
        return ceil(paint.descent() - paint.ascent()).toInt()
    }

    private fun use(line: Line) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * line.scale, metrics)
        paint.typeface = StemFit.typeface(paint.textSize, if (line.bold) maxOf(style.weight, InkStyle.BOLD_WEIGHT) else style.weight)
    }

    private fun dp(value: Float) = (value * metrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 8f
        const val GAP_DP = 4f
        const val COLUMN_GAP_DP = 12f
        const val SMALL_DP = 140f
        const val TALL_DP = 200f
        const val DAY_SCALE = 2.2f
        const val MIN_DAY_SCALE = 1.2f
        const val DATE_SHARE_PERCENT = 40
        const val MONTH_HEIGHT_PERCENT = 70
    }
}
