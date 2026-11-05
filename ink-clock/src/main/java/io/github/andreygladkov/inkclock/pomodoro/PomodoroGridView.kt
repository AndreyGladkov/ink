package io.github.andreygladkov.inkclock.pomodoro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.min
import kotlin.math.roundToInt

/** Past minutes are solid, the minute under way has a heavy frame, the rest a thin one. */
class PomodoroGridView(context: Context) : View(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val line = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val gap = (GAP_DP * density).roundToInt()
    private val paint = Paint().apply { isAntiAlias = false }

    var grid: PomodoroGrid? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val grid = grid ?: return
        val side = min((width - gap * (grid.columns - 1)) / grid.columns, (height - gap * (grid.rows - 1)) / grid.rows)
        if (side <= 0) return
        val left = (width - side * grid.columns - gap * (grid.columns - 1)) / 2
        val top = (height - side * grid.rows - gap * (grid.rows - 1)) / 2
        val radius = min(style.cornerRadiusDp * density, side.toFloat() / MAX_RADIUS_FRACTION)
        for (cell in 0 until grid.cells) {
            val x = (left + cell % grid.columns * (side + gap)).toFloat()
            val y = (top + cell / grid.columns * (side + gap)).toFloat()
            val border = when {
                cell in grid.solid -> 0
                cell == grid.current -> line * CURRENT_LINES
                else -> line
            }
            drawCell(canvas, x, y, side.toFloat(), radius, border)
        }
    }

    private fun drawCell(canvas: Canvas, x: Float, y: Float, side: Float, radius: Float, border: Int) {
        paint.color = style.foreground
        canvas.drawRoundRect(x, y, x + side, y + side, radius, radius, paint)
        if (border == 0) return
        paint.color = style.background
        val inner = (radius - border).coerceAtLeast(0f)
        canvas.drawRoundRect(x + border, y + border, x + side - border, y + side - border, inner, inner, paint)
    }

    private companion object {
        const val GAP_DP = 6f
        const val CURRENT_LINES = 3
        const val MAX_RADIUS_FRACTION = 4
    }
}
