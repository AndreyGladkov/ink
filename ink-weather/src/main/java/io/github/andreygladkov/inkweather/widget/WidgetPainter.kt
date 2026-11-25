package io.github.andreygladkov.inkweather.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.inkweather.R
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

    enum class Shape {
        SMALL, MEDIUM, WIDE, TALL;

        companion object {
            private const val MEDIUM_DP = 140f
            private const val WIDE_DP = 250f
            private const val TALL_DP = 150f

            fun of(widthDp: Float, heightDp: Float): Shape = when {
                widthDp < MEDIUM_DP -> SMALL
                widthDp < WIDE_DP -> MEDIUM
                heightDp < TALL_DP -> WIDE
                else -> TALL
            }
        }
    }

    private data class Spread(val icon: Int, val main: List<Line>, val textWidth: Int, val details: List<Line>)

    private data class Line(val text: String, val scale: Float, val bold: Boolean = false, @param:DrawableRes val icon: Int? = null)

    fun draw(content: WidgetContent, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(style.background)
        val area = Rect(0, 0, width, height).apply { inset(dp(PADDING_DP), dp(PADDING_DP)) }
        content.note?.let { area.bottom -= drawNote(canvas, it, area) }
        val now = content.now
        if (now == null) {
            drawLines(canvas, listOfNotNull(content.place.takeIf { it.isNotEmpty() }?.let { Line(it, 1f, bold = true) }, Line(content.description, InkStyle.SECONDARY_SCALE)), area, Paint.Align.CENTER)
            return bitmap
        }
        when (Shape.of(width / metrics.density, height / metrics.density)) {
            Shape.SMALL -> drawSmall(canvas, now, area)
            Shape.MEDIUM -> drawNow(canvas, content, now, area)
            Shape.WIDE -> drawWide(canvas, content, now, area)
            Shape.TALL -> {
                drawWide(canvas, content, now, Rect(area.left, area.top, area.right, area.centerY()))
                drawSlots(canvas, content.days, Rect(area.left, area.centerY(), area.right, area.bottom), InkStyle.SECONDARY_SCALE)
            }
        }
        return bitmap
    }

    private fun drawSmall(canvas: Canvas, now: WidgetContent.Slot, area: Rect) {
        val temperature = Line(now.value, InkStyle.TITLE_SCALE, bold = true)
        val textHeight = height(temperature)
        val icon = min(area.width(), area.height() - textHeight - dp(GAP_DP)).coerceAtMost(dp(MAX_ICON_DP))
        if (icon < dp(MIN_ICON_DP)) return drawLines(canvas, listOf(temperature), area, Paint.Align.CENTER)
        val top = area.centerY() - (icon + dp(GAP_DP) + textHeight) / 2
        drawIcon(canvas, now.icon, area.centerX() - icon / 2, top, icon)
        drawLine(canvas, temperature, area.left, top + icon + dp(GAP_DP), area.width(), Paint.Align.CENTER)
    }

    private fun drawWide(canvas: Canvas, content: WidgetContent, now: WidgetContent.Slot, area: Rect) {
        val split = area.left + area.width() * NOW_SHARE_PERCENT / 100
        drawNow(canvas, content, now, Rect(area.left, area.top, split, area.bottom))
        val hours = Rect(split + dp(GAP_DP), area.top, area.right, area.bottom)
        drawSlots(canvas, content.hours.take(hours.width() / dp(MIN_SLOT_DP)), hours, 1f)
    }

    private fun drawNow(canvas: Canvas, content: WidgetContent, now: WidgetContent.Slot, area: Rect) {
        val icon = min(area.height(), area.width() * 2 / 5).coerceAtMost(dp(MAX_ICON_DP))
        val temperature = Line(now.value, InkStyle.DISPLAY_SCALE, bold = true)
        val place = Line(content.place, InkStyle.SECONDARY_SCALE)
        val wind = content.wind?.let { Line(it, InkStyle.SECONDARY_SCALE, icon = R.drawable.ic_air) }
        val precipitation = content.precipitation?.let { Line(it, InkStyle.SECONDARY_SCALE, icon = R.drawable.ic_drop) }
        val details = listOfNotNull(wind, precipitation)
        val mostNeeded = listOfNotNull(precipitation ?: wind)
        listOf(listOf(temperature) + details + place, listOf(temperature) + details).firstOrNull { height(it) <= area.height() }
            ?.let { return drawStacked(canvas, now.icon, icon, it, area) }
        val mains = listOf(listOf(temperature, place) to dp(MIN_NOW_ICON_BESIDE_PLACE_DP), listOf(temperature) to dp(MIN_NOW_ICON_DP))
            .filter { (main, _) -> height(main) <= area.height() }
        listOf(details, mostNeeded).distinct().filter { it.isNotEmpty() }
            .flatMap { beside -> mains.map { (main, minIcon) -> fitBeside(beside, main, icon, minIcon, area) } }
            .firstNotNullOfOrNull { it }
            ?.let { return drawSpread(canvas, now.icon, it, area) }
        val lines = listOf(listOf(temperature) + mostNeeded + place, listOf(temperature) + mostNeeded, listOf(temperature, place), listOf(temperature))
            .first { height(it) <= area.height() || it.size == 1 }
        drawStacked(canvas, now.icon, icon, lines, area)
    }

    private fun drawStacked(canvas: Canvas, @DrawableRes nowIcon: Int, icon: Int, lines: List<Line>, area: Rect) {
        val textWidth = width(lines).coerceAtMost(area.width() - icon - dp(GAP_DP))
        val left = area.centerX() - (icon + dp(GAP_DP) + textWidth) / 2
        drawIcon(canvas, nowIcon, left, area.centerY() - icon / 2, icon)
        val textLeft = left + icon + dp(GAP_DP)
        drawLines(canvas, lines, Rect(textLeft, area.top, area.right, area.bottom), Paint.Align.LEFT)
    }

    private fun fitBeside(details: List<Line>, main: List<Line>, maxIcon: Int, minIcon: Int, area: Rect): Spread? {
        if (height(details) > area.height()) return null
        val textWidth = width(main)
        val icon = min(maxIcon, area.width() - dp(GAP_DP) - textWidth - dp(COLUMN_GAP_DP) - width(details))
        return Spread(icon, main, textWidth, details).takeIf { icon >= minIcon }
    }

    private fun drawSpread(canvas: Canvas, @DrawableRes nowIcon: Int, spread: Spread, area: Rect) {
        drawIcon(canvas, nowIcon, area.left, area.centerY() - spread.icon / 2, spread.icon)
        val textLeft = area.left + spread.icon + dp(GAP_DP)
        drawLines(canvas, spread.main, Rect(textLeft, area.top, textLeft + spread.textWidth, area.bottom), Paint.Align.LEFT)
        drawLines(canvas, spread.details, Rect(area.right - width(spread.details), area.top, area.right, area.bottom), Paint.Align.LEFT)
    }

    private fun drawSlots(canvas: Canvas, slots: List<WidgetContent.Slot>, area: Rect, valueScale: Float) {
        if (slots.isEmpty()) return
        val column = area.width() / slots.size
        val gap = dp(GAP_DP)
        val labelHeight = height(Line("", InkStyle.SECONDARY_SCALE))
        val valueHeight = height(Line("", valueScale))
        val room = area.height() - labelHeight - valueHeight - 2 * gap
        val icon = min(column * 3 / 5, room).coerceAtMost(dp(MAX_SLOT_ICON_DP)).takeIf { it >= dp(MIN_ICON_DP) } ?: 0
        val iconRow = if (icon > 0) icon + gap else 0
        val top = area.centerY() - (labelHeight + gap + iconRow + valueHeight) / 2
        slots.forEachIndexed { index, slot ->
            val left = area.left + index * column
            drawLine(canvas, Line(slot.label, InkStyle.SECONDARY_SCALE), left, top, column, Paint.Align.CENTER)
            drawIcon(canvas, slot.icon, left + (column - icon) / 2, top + labelHeight + gap, icon)
            drawLine(canvas, Line(slot.value, valueScale), left, top + labelHeight + gap + iconRow, column, Paint.Align.CENTER)
        }
    }

    private fun drawNote(canvas: Canvas, note: String, area: Rect): Int {
        val line = Line(note, NOTE_SCALE)
        val height = height(line)
        drawLine(canvas, line, area.left, area.bottom - height, area.width(), Paint.Align.RIGHT)
        return height + dp(GAP_DP)
    }

    private fun drawLines(canvas: Canvas, lines: List<Line>, area: Rect, align: Paint.Align) {
        var top = area.centerY() - height(lines) / 2
        lines.forEach { line ->
            drawLine(canvas, line, area.left, top, area.width(), align)
            top += height(line)
        }
    }

    private fun drawLine(canvas: Canvas, line: Line, left: Int, top: Int, width: Int, align: Paint.Align) {
        use(line)
        val lead = lead(line)
        val text = TextUtils.ellipsize(line.text, paint, (width - lead).toFloat(), TextUtils.TruncateAt.END).toString()
        val used = lead + ceil(paint.measureText(text)).toInt()
        val start = when (align) {
            Paint.Align.LEFT -> left
            Paint.Align.CENTER -> left + (width - used) / 2
            Paint.Align.RIGHT -> left + width - used
        }
        val baseline = (top - paint.ascent()).roundToInt()
        line.icon?.let { drawIcon(canvas, it, start, baseline - capHeight() / 2 - iconSize() / 2, iconSize()) }
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(text, (start + lead).toFloat(), baseline.toFloat(), paint)
    }

    private fun lead(line: Line): Int = if (line.icon == null) 0 else iconSize() + iconSize() / ICON_GAP_DIVISOR

    private fun iconSize(): Int = (capHeight() * ICON_TO_CAP_HEIGHT).roundToInt()

    private fun capHeight(): Int = Rect().also { paint.getTextBounds(CAP_SAMPLE, 0, CAP_SAMPLE.length, it) }.height()

    private fun drawIcon(canvas: Canvas, @DrawableRes icon: Int, left: Int, top: Int, size: Int) {
        if (size <= 0) return
        canvas.drawBitmap(MonochromeIcons.bitmap(context, icon, size, style.foreground), left.toFloat(), top.toFloat(), null)
    }

    private fun width(line: Line): Int {
        use(line)
        return lead(line) + ceil(paint.measureText(line.text)).toInt()
    }

    private fun width(lines: List<Line>): Int = lines.maxOf(::width)

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
        const val MIN_ICON_DP = 16f
        const val MAX_ICON_DP = 64f
        const val MIN_NOW_ICON_DP = 28f
        const val MIN_NOW_ICON_BESIDE_PLACE_DP = 40f
        const val COLUMN_GAP_DP = 12f
        const val MAX_SLOT_ICON_DP = 32f
        const val MIN_SLOT_DP = 44f
        const val NOW_SHARE_PERCENT = 45
        const val NOTE_SCALE = 0.7f
        const val CAP_SAMPLE = "0"
        const val ICON_TO_CAP_HEIGHT = 1.3f
        const val ICON_GAP_DIVISOR = 4
    }
}
