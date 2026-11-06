package io.github.andreygladkov.inkclock.pomodoro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.PathParser
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inkclock.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The tomato of res/values/tomato.xml drawn large, with the dial's black sector inside the body. The leaves lie over
 * the body with a background-coloured edge, so they stay visible on both the black and the eaten part; the
 * highlight inverts whatever lies under it for the same reason.
 */
class TomatoView(context: Context) : View(context) {
    private val inkStyle = InkStyle.of(context)
    private val outlinePx = (InkStyle.LINE_DP * OUTLINE_LINES * resources.displayMetrics.density).roundToInt().coerceAtLeast(1).toFloat()
    private val ink = Paint().apply {
        isAntiAlias = false
        color = inkStyle.foreground
    }
    private val paper = Paint(ink).apply { color = inkStyle.background }
    private val outline = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = outlinePx
        color = inkStyle.foreground
    }
    private val halo = Paint(outline).apply {
        strokeWidth = outlinePx * 2
        color = inkStyle.background
    }
    private val bodyShape = PathParser.createPathFromPathData(context.getString(R.string.tomato_body))
    private val calyxShape = PathParser.createPathFromPathData(context.getString(R.string.tomato_calyx))
    private val highlightShape = PathParser.createPathFromPathData(context.getString(R.string.tomato_highlight))
    private val body = Path()
    private val calyx = Path()
    private val highlight = Path()
    private val sector = RectF()
    private val flesh = Path()
    private val highlightOnFlesh = Path()
    private val highlightOnEaten = Path()

    var dial: PomodoroDial? = null
        set(value) {
            if (field == value) return
            field = value
            shapeFlesh()
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val scale = min((w - outlinePx * 2) / UNITS_WIDE, (h - outlinePx * 2) / UNITS_TALL)
        val matrix = Matrix().apply {
            setTranslate(-LEFT, -TOP)
            postScale(scale, scale)
            postTranslate((w - UNITS_WIDE * scale) / 2, (h - UNITS_TALL * scale) / 2)
        }
        body.set(bodyShape)
        body.transform(matrix)
        calyx.set(calyxShape)
        calyx.transform(matrix)
        highlight.set(highlightShape)
        highlight.transform(matrix)
        val center = floatArrayOf(CENTER_X, CENTER_Y).also(matrix::mapPoints)
        val radius = RADIUS * scale
        sector.set(center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius)
        shapeFlesh()
    }

    private fun shapeFlesh() {
        val dial = dial ?: return
        flesh.reset()
        when {
            dial.black >= dial.minutes -> flesh.addOval(sector, Path.Direction.CW)
            dial.black > 0 -> {
                flesh.moveTo(sector.centerX(), sector.centerY())
                flesh.arcTo(sector, dial.startDegrees, dial.sweepDegrees)
                flesh.close()
            }
        }
        highlightOnFlesh.op(highlight, flesh, Path.Op.INTERSECT)
        highlightOnEaten.op(highlight, flesh, Path.Op.DIFFERENCE)
    }

    override fun onDraw(canvas: Canvas) {
        if (dial == null) return
        canvas.save()
        canvas.clipPath(body)
        canvas.drawColor(inkStyle.background)
        canvas.drawPath(flesh, ink)
        canvas.restore()
        canvas.drawPath(highlightOnFlesh, paper)
        canvas.drawPath(highlightOnEaten, ink)
        canvas.drawPath(calyx, halo)
        canvas.drawPath(body, outline)
        canvas.drawPath(calyx, ink)
    }

    private companion object {
        const val OUTLINE_LINES = 3
        const val LEFT = 2.8f
        const val TOP = 5.6f
        const val UNITS_WIDE = 18.4f
        const val UNITS_TALL = 16.7f
        const val CENTER_X = 12f
        const val CENTER_Y = 14.7f
        const val RADIUS = 12f
    }
}
