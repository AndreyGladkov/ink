package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.View
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

class VerticalDivider(context: Context) : View(context) {
    private val heightPx = (HEIGHT_DP * resources.displayMetrics.density).roundToInt()

    init {
        setBackgroundColor(InkStyle.of(context).foreground)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(widthPx(context), heightPx)

    companion object {
        private const val HEIGHT_DP = 28f

        fun widthPx(context: Context) = (InkStyle.LINE_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }
}
