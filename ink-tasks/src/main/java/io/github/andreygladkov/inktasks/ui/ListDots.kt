package io.github.andreygladkov.inktasks.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inktasks.R
import kotlin.math.roundToInt

/** Which of the lists is open, drawn like Ink Launcher's page dots: a large dot for it and small ones for the rest. */
class ListDots(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val dotPx = (DOT_DP * density).roundToInt()
    private val smallPx = (SMALL_DOT_DP * density).roundToInt()
    private val stepPx = (STEP_DP * density).roundToInt()
    private val minStepPx = (MIN_STEP_DP * density).roundToInt()
    private val gapPx = (GAP_DP * density).roundToInt()
    private val fill = Paint().apply {
        isAntiAlias = false
        color = InkStyle.of(context).foreground
    }
    private var index = 0
    private var count = 0

    fun show(index: Int, count: Int) {
        if (index == this.index && count == this.count) return
        this.index = index
        this.count = count
        contentDescription = if (count > 1 && index in 0 until count) context.getString(R.string.list_position, index + 1, count) else null
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, if (fits(width)) dotPx + gapPx else 0)
    }

    override fun onDraw(canvas: Canvas) {
        if (!fits(width)) return
        val step = step(width)
        val centerY = (gapPx + dotPx / 2f).roundToInt().toFloat()
        val first = width / 2f - (count - 1) * step / 2f
        for (i in 0 until count) {
            val radius = if (i == index) dotPx / 2f else smallPx / 2f
            canvas.drawCircle((first + i * step).roundToInt().toFloat(), centerY, radius, fill)
        }
    }

    private fun fits(width: Int) = index in 0 until count && count > 1 && step(width) >= minStepPx

    private fun step(width: Int): Int = minOf(stepPx, (width - dotPx) / (count - 1))

    private companion object {
        const val DOT_DP = 10f
        const val SMALL_DOT_DP = 4f
        const val STEP_DP = 20f
        const val MIN_STEP_DP = 10f
        const val GAP_DP = 4f
    }
}
