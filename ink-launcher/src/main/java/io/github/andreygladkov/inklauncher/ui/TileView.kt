package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import kotlin.math.roundToInt

/** An icon over a one-line label; pressed or selected, the cell turns solid and its ink turns to background. */
class TileView(
    context: Context,
    val label: String?,
    private val iconOf: (sizePx: Int) -> Bitmap,
) : View(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val haptics by lazy { Haptics.of(context) }
    private val fill = Paint().apply { isAntiAlias = false }
    private val iconPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val text = TextPaint().apply {
        isAntiAlias = false
        textAlign = Paint.Align.CENTER
    }
    private val inkFilter = PorterDuffColorFilter(style.foreground, PorterDuff.Mode.SRC_IN)
    private val invertedFilter = PorterDuffColorFilter(style.background, PorterDuff.Mode.SRC_IN)
    private var icon: Bitmap? = null
    private var iconTop = 0
    var shownLabel: CharSequence = ""
        private set
    private var baseline = 0f

    val labelTextSizePx: Float get() = text.textSize

    init {
        isClickable = true
        isLongClickable = true
        contentDescription = label
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val padding = (PADDING_DP * density).roundToInt()
        val labelHeight = if (label.isNullOrEmpty()) 0 else fitLabel(w - 2 * padding)
        val gap = if (labelHeight > 0) (GAP_DP * density).roundToInt() else 0
        val size = minOf(w - 2 * padding, h - 2 * padding - labelHeight - gap, (MAX_ICON_DP * density).roundToInt()).coerceAtLeast(1)
        icon = iconOf(size)
        iconTop = (h - size - gap - labelHeight) / 2
        baseline = iconTop + size + gap - text.ascent()
    }

    private fun fitLabel(widthPx: Int): Int {
        text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * InkStyle.SECONDARY_SCALE, resources.displayMetrics)
        text.typeface = StemFit.typeface(text.textSize, style.weight)
        shownLabel = TextUtils.ellipsize(label.orEmpty(), text, widthPx.toFloat(), TextUtils.TruncateAt.END)
        return (text.descent() - text.ascent()).roundToInt()
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

    override fun onDraw(canvas: Canvas) {
        val inverted = isPressed || isSelected
        val ink = if (inverted) style.background else style.foreground
        if (inverted) {
            val radius = style.cornerRadiusDp * density
            fill.color = style.foreground
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fill)
        }
        icon?.let { bitmap ->
            iconPaint.colorFilter = if (inverted) invertedFilter else inkFilter
            canvas.drawBitmap(bitmap, ((width - bitmap.width) / 2).toFloat(), iconTop.toFloat(), iconPaint)
        }
        if (shownLabel.isNotEmpty()) {
            text.color = ink
            canvas.drawText(shownLabel, 0, shownLabel.length, width / 2f, baseline.roundToInt().toFloat(), text)
        }
    }

    private companion object {
        const val PADDING_DP = 4f
        const val GAP_DP = 4f
        const val MAX_ICON_DP = 56f
    }
}
