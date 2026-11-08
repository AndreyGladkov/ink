package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

fun Canvas.drawDriftingMark(left: Float, top: Float, size: Int, borderPx: Int, drifting: Boolean, ink: Int, paper: Int, paint: Paint) {
    paint.color = ink
    drawRect(left, top, left + size, top + size, paint)
    if (drifting) return
    paint.color = paper
    drawRect(left + borderPx, top + borderPx, left + size - borderPx, top + size - borderPx, paint)
}

class DriftingMark(context: Context, val isDrifting: Boolean) : View(context) {
    private val style = InkStyle.of(context)
    private val borderPx = (InkStyle.LINE_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * SCALE, resources.displayMetrics).roundToInt()
    private val paint = Paint().apply { isAntiAlias = false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(canvas: Canvas) = canvas.drawDriftingMark(0f, 0f, sizePx, borderPx, isDrifting, style.foreground, style.background, paint)

    private companion object {
        const val SCALE = 0.7f
    }
}
