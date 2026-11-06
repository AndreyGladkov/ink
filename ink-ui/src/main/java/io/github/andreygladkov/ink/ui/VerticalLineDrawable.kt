package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

/** A short line centred in whatever height a horizontal LinearLayout gives its divider. */
class VerticalLineDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val widthPx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
    private val heightPx = (HEIGHT_DP * density).roundToInt()
    private val paint = Paint().apply {
        isAntiAlias = false
        color = InkStyle.of(context).foreground
    }

    override fun getIntrinsicWidth() = widthPx

    override fun getIntrinsicHeight() = heightPx

    override fun draw(canvas: Canvas) {
        val height = minOf(heightPx, bounds.height())
        val top = bounds.top + (bounds.height() - height) / 2
        canvas.drawRect(bounds.left.toFloat(), top.toFloat(), (bounds.left + widthPx).toFloat(), (top + height).toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val HEIGHT_DP = 28f
    }
}
