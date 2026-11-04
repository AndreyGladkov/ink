package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

class DottedDrawable(context: Context) : Drawable() {
    private val dotPx = (DOT_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply {
        isAntiAlias = false
        color = InkStyle.of(context).foreground
    }

    override fun getIntrinsicHeight() = dotPx

    override fun draw(canvas: Canvas) {
        val top = bounds.top + (bounds.height() - dotPx) / 2
        var x = bounds.left
        while (x < bounds.right) {
            canvas.drawRect(x.toFloat(), top.toFloat(), (x + dotPx).toFloat(), (top + dotPx).toFloat(), paint)
            x += dotPx * SPACING
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val DOT_DP = 1f
        const val SPACING = 3
    }
}
