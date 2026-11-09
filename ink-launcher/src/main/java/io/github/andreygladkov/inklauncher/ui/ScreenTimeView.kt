package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.withTranslation
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeText
import kotlin.math.floor
import kotlin.math.roundToInt

class ScreenTimeView(context: Context) : View(context) {
    data class Usage(val drifting: String, val onPurpose: String, val driftingShare: Float, val unlocks: Int)

    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val padding = (PADDING_DP * density).roundToInt()
    private val borderPx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val haptics by lazy { Haptics.of(context) }
    private val fill = Paint().apply { isAntiAlias = false }
    private val iconPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val values = TextPaint().apply { isAntiAlias = false }
    private val names = TextPaint().apply { isAntiAlias = false }
    private val counts = TextPaint().apply { isAntiAlias = false }
    private val barPath = Path()
    private val capBounds = Rect()
    private val fullSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp, resources.displayMetrics)
    private val driftingName = context.getString(R.string.drifting)
    private val onPurposeName = context.getString(R.string.on_purpose)

    var usage: Usage? = null
        private set
    var message: String? = null
        private set
    var isCompact = false
        private set
    private var messageLayout: StaticLayout? = null
    private var barHeight = 0f

    init {
        isClickable = true
        isLongClickable = true
    }

    fun showUsage(usage: Usage) {
        if (usage == this.usage && message == null) return
        this.usage = usage
        message = null
        contentDescription = "$driftingName ${usage.drifting}, $onPurposeName ${usage.onPurpose}, ${ScreenTimeText.unlocks(context, usage.unlocks)}"
        fit()
    }

    fun showMessage(text: String) {
        if (text == message) return
        message = text
        usage = null
        contentDescription = text
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed && isEnabled) haptics.perform(HapticChannel.BUTTONS)
        super.setPressed(pressed)
        invalidate()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        invalidate()
    }

    private fun fit() {
        invalidate()
        val width = width - 2 * padding
        val height = height - 2 * padding
        if (width <= 0 || height <= 0) return
        val text = message
        if (text != null) {
            shrinkUntil(fullSizePx) { messageLayout(text, width).height <= height }
            messageLayout = messageLayout(text, width)
            return
        }
        isCompact = false
        val wide = fitLargest(width, height)
        if (wide < fullSizePx * MIN_WIDE_SCALE) {
            isCompact = true
            if (fitLargest(width, height) <= wide) {
                isCompact = false
                textSize(wide)
            }
        }
        barHeight = (minBarHeight() + (height - contentHeight()) * BAR_GROWTH)
            .coerceAtMost(valueLine() * MAX_BAR_LINES)
            .coerceAtLeast(borderPx * MIN_BAR_BORDERS)
    }

    private fun fitLargest(width: Int, height: Int): Float {
        val largest = fullSizePx * MAX_SCALE
        textSize(largest)
        val estimate = minOf(largest, largest * width / contentWidth(), largest * height / contentHeight())
        return shrinkUntil(floor(estimate)) { contentWidth() <= width && contentHeight() <= height }
    }

    /** Steps down a pixel at a time, since the typeface fitted to each size measures a little differently. */
    private fun shrinkUntil(from: Float, fits: () -> Boolean): Float {
        val smallest = fullSizePx * MIN_SCALE
        var size = from.coerceAtLeast(smallest)
        textSize(size)
        while (!fits() && size - 1 >= smallest) {
            size -= 1
            textSize(size)
        }
        return size
    }

    private fun textSize(px: Float) {
        val bold = maxOf(style.weight, InkStyle.BOLD_WEIGHT)
        values.textSize = px
        values.typeface = StemFit.typeface(px, bold)
        val secondary = px * InkStyle.SECONDARY_SCALE
        names.textSize = secondary
        names.typeface = StemFit.typeface(secondary, style.weight)
        counts.textSize = secondary
        counts.typeface = StemFit.typeface(secondary, bold)
    }

    private fun messageLayout(text: String, width: Int): StaticLayout {
        names.textSize = values.textSize
        names.typeface = StemFit.typeface(values.textSize, style.weight)
        return StaticLayout.Builder.obtain(text, 0, text.length, names, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    private fun valueLine() = values.descent() - values.ascent()

    private fun nameLine() = names.descent() - names.ascent()

    private fun gap() = valueLine() * GAP_LINES

    private fun minBarHeight() = valueLine() * MIN_BAR_LINES

    private fun capHeight(): Int {
        values.getTextBounds(CAP, 0, CAP.length, capBounds)
        return capBounds.height()
    }

    private fun markSize() = (capHeight() * MARK_SCALE).roundToInt()

    private fun iconSize() = (-counts.ascent()).roundToInt()

    private fun unlocksWidth(unlocks: Int) = iconSize() + counts.measureText(" $unlocks")

    private fun contentWidth(): Float {
        val usage = usage ?: return 0f
        val unlocks = unlocksWidth(usage.unlocks)
        if (isCompact) {
            val times = maxOf(values.measureText(usage.drifting), values.measureText(usage.onPurpose))
            return maxOf(markSize() + values.measureText(" ") + times, unlocks)
        }
        val left = maxOf(names.measureText(driftingName), values.measureText(usage.drifting))
        val right = maxOf(names.measureText(onPurposeName), values.measureText(usage.onPurpose))
        return maxOf(left + values.measureText("  ") + right, unlocks)
    }

    private fun contentHeight(bar: Float = minBarHeight()): Float {
        val times = if (isCompact) valueLine() * 2 else nameLine() + valueLine()
        return times + gap() + bar + gap() + nameLine()
    }

    override fun onDraw(canvas: Canvas) {
        val inverted = isPressed || isSelected
        val ink = if (inverted) style.background else style.foreground
        val paper = if (inverted) style.foreground else style.background
        if (inverted) {
            val radius = style.cornerRadiusDp * density
            fill.color = style.foreground
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fill)
        }
        listOf(values, names, counts).forEach { it.color = ink }
        val layout = messageLayout
        if (message != null && layout != null) {
            canvas.withTranslation(padding.toFloat(), ((height - layout.height) / 2).toFloat()) { layout.draw(this) }
            return
        }
        val usage = usage ?: return
        val left = padding.toFloat()
        val right = (width - padding).toFloat()
        var top = ((height - contentHeight(barHeight)) / 2).roundToInt().toFloat()
        top = if (isCompact) drawStackedTimes(canvas, usage, left, top, ink, paper) else drawNamedTimes(canvas, usage, left, right, top)
        top = (top + gap()).roundToInt().toFloat()
        val barBottom = top + barHeight.roundToInt()
        drawBar(canvas, left, top, right, barBottom, usage.driftingShare, ink, paper)
        drawUnlocks(canvas, usage.unlocks, if (isCompact) left else right - unlocksWidth(usage.unlocks), (barBottom + gap()).roundToInt().toFloat(), ink)
    }

    private fun drawNamedTimes(canvas: Canvas, usage: Usage, left: Float, right: Float, top: Float): Float {
        val nameBaseline = (top - names.ascent()).roundToInt().toFloat()
        textAt(canvas, driftingName, left, nameBaseline, names, Paint.Align.LEFT)
        textAt(canvas, onPurposeName, right, nameBaseline, names, Paint.Align.RIGHT)
        val valueTop = top + nameLine()
        val valueBaseline = (valueTop - values.ascent()).roundToInt().toFloat()
        textAt(canvas, usage.drifting, left, valueBaseline, values, Paint.Align.LEFT)
        textAt(canvas, usage.onPurpose, right, valueBaseline, values, Paint.Align.RIGHT)
        return valueTop + valueLine()
    }

    private fun drawStackedTimes(canvas: Canvas, usage: Usage, left: Float, top: Float, ink: Int, paper: Int): Float {
        var lineTop = top
        listOf(usage.drifting to AppCategory.DRIFTING, usage.onPurpose to AppCategory.ON_PURPOSE).forEach { (time, category) ->
            val baseline = (lineTop - values.ascent()).roundToInt().toFloat()
            drawMark(canvas, left, baseline, category, ink, paper)
            textAt(canvas, time, left + markSize() + values.measureText(" "), baseline, values, Paint.Align.LEFT)
            lineTop += valueLine()
        }
        return lineTop
    }

    private fun textAt(canvas: Canvas, text: String, x: Float, baseline: Float, paint: TextPaint, align: Paint.Align) {
        paint.textAlign = align
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawMark(canvas: Canvas, left: Float, baseline: Float, category: AppCategory, ink: Int, paper: Int) {
        val size = markSize()
        val top = (baseline - (capHeight() + size) / 2f).roundToInt().toFloat()
        canvas.drawCategoryMark(left, top, size, borderPx, category, ink, paper, fill)
    }

    /** A nonzero share always fills at least a sliver, so a few minutes of drifting do not vanish from the bar. */
    private fun drawBar(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, share: Float, ink: Int, paper: Int) {
        val radius = (style.cornerRadiusDp * density).coerceAtMost((bottom - top) / 2)
        fill.color = ink
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)
        val inner = (radius - borderPx).coerceAtLeast(0f)
        fill.color = paper
        canvas.drawRoundRect(left + borderPx, top + borderPx, right - borderPx, bottom - borderPx, inner, inner, fill)
        val span = (right - left).roundToInt() - 2 * borderPx
        if (share <= 0f || span <= 0) return
        val filled = (span * share).roundToInt().coerceIn(minOf(borderPx, span), span)
        val end = if (filled == span) inner else 0f
        barPath.reset()
        barPath.addRoundRect(left + borderPx, top + borderPx, left + borderPx + filled, bottom - borderPx,
            floatArrayOf(inner, inner, end, end, end, end, inner, inner), Path.Direction.CW)
        fill.color = ink
        canvas.drawPath(barPath, fill)
    }

    private fun drawUnlocks(canvas: Canvas, unlocks: Int, left: Float, top: Float, ink: Int) {
        val icon = MonochromeIcons.bitmap(context, R.drawable.ic_lock_open, iconSize(), ink)
        val baseline = (top - counts.ascent()).roundToInt().toFloat()
        canvas.drawBitmap(icon, left, baseline - icon.height, iconPaint)
        textAt(canvas, " $unlocks", left + icon.width, baseline, counts, Paint.Align.LEFT)
    }

    private companion object {
        const val PADDING_DP = 8f
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 1.5f
        const val MIN_WIDE_SCALE = 0.75f
        const val GAP_LINES = 0.2f
        const val MIN_BAR_LINES = 0.45f
        const val MAX_BAR_LINES = 1.2f
        const val BAR_GROWTH = 0.5f
        const val MIN_BAR_BORDERS = 3f
        const val MARK_SCALE = 0.8f
        const val CAP = "0"
    }
}
