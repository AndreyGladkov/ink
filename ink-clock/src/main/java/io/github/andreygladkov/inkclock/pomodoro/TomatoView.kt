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
 * the body with a background-coloured edge, so they stay visible on both the black and the eaten part.
 */
class TomatoView(context: Context) : View(context) {
    private val inkStyle = InkStyle.of(context)
    private val outlinePx = (InkStyle.LINE_DP * OUTLINE_LINES * resources.displayMetrics.density).roundToInt().coerceAtLeast(1).toFloat()
    private val fill = Paint().apply { isAntiAlias = false }
    private val stroke = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeWidth = outlinePx
    }
    private val bodyShape = PathParser.createPathFromPathData(context.getString(R.string.tomato_body))
    private val calyxShape = PathParser.createPathFromPathData(context.getString(R.string.tomato_calyx))
    private val body = Path()
    private val calyx = Path()
    private val sector = RectF()

    var dial: PomodoroDial? = null
        set(value) {
            if (field == value) return
            field = value
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
        val center = floatArrayOf(CENTER_X, CENTER_Y).also(matrix::mapPoints)
        val radius = RADIUS * scale
        sector.set(center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius)
    }

    override fun onDraw(canvas: Canvas) {
        val dial = dial ?: return
        stroke.color = inkStyle.foreground
        fill.color = inkStyle.foreground
        canvas.save()
        canvas.clipPath(body)
        canvas.drawColor(inkStyle.background)
        if (dial.black > 0) canvas.drawArc(sector, dial.startDegrees, dial.sweepDegrees, true, fill)
        canvas.restore()
        canvas.drawPath(body, stroke)
        stroke.color = inkStyle.background
        canvas.drawPath(calyx, stroke)
        canvas.drawPath(calyx, fill)
    }

    private companion object {
        const val OUTLINE_LINES = 2
        const val LEFT = 3f
        const val TOP = 2f
        const val UNITS_WIDE = 18f
        const val UNITS_TALL = 20f
        const val CENTER_X = 12f
        const val CENTER_Y = 14.5f
        const val RADIUS = 12f
    }
}
