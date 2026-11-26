package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import kotlin.math.ceil
import kotlin.math.roundToInt

class DayCell(context: Context) : InkTextView(context) {
    var isToday: Boolean = false
        set(value) {
            field = value
            refreshTextColor()
            invalidate()
        }

    var isMarked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val haptics by lazy { Haptics.of(context) }
    private val density = resources.displayMetrics.density
    private val fill = Paint().apply { isAntiAlias = false }
    private val ringPx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val isFilled get() = isToday || isPressed

    override val textColor: Int get() = if (isFilled) style.background else style.foreground

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }

    private val radius get() = textSize * CIRCLE_TO_TEXT_SIZE
    private val dotRadius get() = DOT_RADIUS_DP * density
    private val dotGap get() = DOT_GAP_DP * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val needed = ceil(2 * (radius + dotGap + 2 * dotRadius)).toInt()
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY && measuredHeight < needed) setMeasuredDimension(measuredWidth, needed)
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed && isEnabled && isClickable) haptics.perform(HapticChannel.BUTTONS)
        super.setPressed(pressed)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        refreshTextColor()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val circle = minOf(radius, centerX)
        fill.color = style.foreground
        if (isFilled) {
            canvas.drawCircle(centerX, centerY, circle, fill)
        } else if (isSelected) {
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = ringPx.toFloat()
            canvas.drawCircle(centerX, centerY, circle - ringPx / 2f, fill)
            fill.style = Paint.Style.FILL
        }
        if (isMarked) canvas.drawCircle(centerX, centerY + circle + dotGap + dotRadius, dotRadius, fill)
        super.onDraw(canvas)
    }

    private companion object {
        const val CIRCLE_TO_TEXT_SIZE = 0.9f
        const val DOT_RADIUS_DP = 2.5f
        const val DOT_GAP_DP = 3f
    }
}
