package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import kotlin.math.roundToInt

fun Canvas.drawCategoryMark(left: Float, top: Float, size: Int, borderPx: Int, category: AppCategory, ink: Int, paper: Int, paint: Paint) {
    paint.color = ink
    if (category == AppCategory.UNCOUNTED) {
        val lineTop = top + ((size - borderPx) / 2)
        drawRect(left, lineTop, left + size, lineTop + borderPx, paint)
        return
    }
    drawRect(left, top, left + size, top + size, paint)
    if (category == AppCategory.DRIFTING) return
    paint.color = paper
    drawRect(left + borderPx, top + borderPx, left + size - borderPx, top + size - borderPx, paint)
}

class CategoryMark(context: Context, val category: AppCategory) : View(context) {
    private val style = InkStyle.of(context)
    private val borderPx = (InkStyle.LINE_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * SCALE, resources.displayMetrics).roundToInt()
    private val paint = Paint().apply { isAntiAlias = false }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(sizePx, sizePx)

    override fun onDraw(canvas: Canvas) = canvas.drawCategoryMark(0f, 0f, sizePx, borderPx, category, style.foreground, style.background, paint)

    private companion object {
        const val SCALE = 0.7f
    }
}
