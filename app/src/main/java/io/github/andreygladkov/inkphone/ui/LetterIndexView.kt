package io.github.andreygladkov.inkphone.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.eink.StemFit
import io.github.andreygladkov.inkphone.haptics.HapticChannel
import io.github.andreygladkov.inkphone.haptics.Haptics
import kotlin.math.roundToInt

class LetterIndexView(context: Context, private val onPick: (String) -> Unit) : View(context) {
    private val style = InkStyle.of(context)
    private val haptics by lazy { Haptics.of(context) }
    private val density = resources.displayMetrics.density
    private val minSlotPx = (MIN_SLOT_DP * density).roundToInt()
    private val linePx = (LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val fill = Paint().apply { isAntiAlias = false }
    private val text = Paint().apply {
        isAntiAlias = false
        textAlign = Paint.Align.CENTER
    }

    var letters: List<String> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var current: String? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((WIDTH_DP * density).roundToInt(), widthMeasureSpec),
            getDefaultSize(suggestedMinimumHeight, heightMeasureSpec),
        )
    }

    internal fun slots(heightPx: Int): List<List<String>> {
        if (letters.isEmpty()) return emptyList()
        val count = minOf(letters.size, (heightPx / minSlotPx).coerceAtLeast(1))
        return (0 until count).map { i -> letters.subList(i * letters.size / count, (i + 1) * letters.size / count) }
    }

    override fun onDraw(canvas: Canvas) {
        fill.color = style.foreground
        canvas.drawRect(0f, 0f, linePx.toFloat(), height.toFloat(), fill)
        val slots = slots(height)
        if (slots.isEmpty()) return
        val slotHeight = height / slots.size
        text.textSize = minOf(style.textSizeSp * density * TEXT_SCALE, slotHeight * MAX_TEXT_TO_SLOT)
        text.typeface = StemFit.typeface(text.textSize, InkStyle.BOLD_WEIGHT)
        val centerX = (linePx + width) / 2f
        slots.forEachIndexed { i, slot ->
            val top = i * height / slots.size
            val bottom = (i + 1) * height / slots.size
            val isCurrent = current in slot
            if (isCurrent) canvas.drawRect(linePx.toFloat(), top.toFloat(), width.toFloat(), bottom.toFloat(), fill)
            text.color = if (isCurrent) style.background else style.foreground
            val baseline = (top + bottom) / 2f - (text.ascent() + text.descent()) / 2f
            canvas.drawText(slot.first(), centerX, baseline.roundToInt().toFloat(), text)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> haptics.perform(HapticChannel.BUTTONS)
            MotionEvent.ACTION_UP -> {
                val slots = slots(height)
                if (slots.isNotEmpty()) {
                    val index = (event.y * slots.size / height).toInt().coerceIn(0, slots.lastIndex)
                    onPick(slots[index].first())
                }
            }
        }
        return true
    }

    private companion object {
        const val WIDTH_DP = 40f
        const val MIN_SLOT_DP = 24f
        const val LINE_DP = 2f
        const val TEXT_SCALE = 0.8f
        const val MAX_TEXT_TO_SLOT = 0.8f
    }
}
