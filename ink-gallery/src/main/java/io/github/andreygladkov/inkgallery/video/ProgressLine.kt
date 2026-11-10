package io.github.andreygladkov.inkgallery.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

/** How far a video has played, as a thin line filled up to that point; a tap on it seeks there. */
class ProgressLine(context: Context, private val onSeek: (fraction: Float) -> Unit) : View(context) {
    var fraction = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped == field) return
            field = clamped
            invalidate()
        }

    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val paint = Paint().apply { isAntiAlias = false }
    private val sidePx = SIDE_DP * density
    private val linePx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val barPx = (BAR_DP * density).roundToInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (TOUCH_DP * density).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val middle = height / 2f
        val right = width - sidePx
        paint.color = style.foreground
        canvas.drawRect(sidePx, middle - linePx / 2f, right, middle + linePx / 2f, paint)
        canvas.drawRect(sidePx, middle - barPx / 2f, sidePx + (right - sidePx) * fraction, middle + barPx / 2f, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) onSeek(((event.x - sidePx) / (width - 2 * sidePx)).coerceIn(0f, 1f))
        return true
    }

    private companion object {
        const val SIDE_DP = 16f
        const val TOUCH_DP = 40f
        const val BAR_DP = 8f
    }
}
