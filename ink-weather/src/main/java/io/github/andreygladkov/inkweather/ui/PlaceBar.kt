package io.github.andreygladkov.inkweather.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkweather.R
import kotlin.math.roundToInt

class PlaceBar(context: Context, onPrev: () -> Unit, onNext: () -> Unit) : LinearLayout(context) {
    private val prev = iconButton(context, R.drawable.ic_chevron_left, context.getString(R.string.prev_place)).apply { setOnClickListener { onPrev() } }
    private val next = iconButton(context, R.drawable.ic_chevron_right, context.getString(R.string.next_place)).apply { setOnClickListener { onNext() } }
    private val dots = Dots(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(prev)
        addView(dots, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(next)
    }

    fun show(index: Int, count: Int) {
        visibility = if (count > 1) VISIBLE else GONE
        prev.visibility = if (index > 0) VISIBLE else INVISIBLE
        next.visibility = if (index < count - 1) VISIBLE else INVISIBLE
        dots.show(index, count)
    }

    private class Dots(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
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
            contentDescription = "${index + 1} / $count"
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val step = (STEP_DP * density).roundToInt()
            val shown = count.coerceAtMost(width / step)
            val first = width / 2 - (shown - 1) * step / 2
            val centerY = (height / 2f).roundToInt().toFloat()
            for (i in 0 until shown) {
                val radius = (if (i == index) DOT_DP else SMALL_DOT_DP) * density / 2
                canvas.drawCircle((first + i * step).toFloat(), centerY, radius, fill)
            }
        }

        private companion object {
            const val DOT_DP = 10f
            const val SMALL_DOT_DP = 4f
            const val STEP_DP = 20f
        }
    }
}
