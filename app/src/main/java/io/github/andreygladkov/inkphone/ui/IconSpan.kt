package io.github.andreygladkov.inkphone.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import androidx.annotation.DrawableRes
import io.github.andreygladkov.inkphone.eink.MonochromeIcons
import kotlin.math.roundToInt

/** Draws an icon in place of the spanned text, in the paint's current color so it inverts with the row. */
class IconSpan(private val context: Context, @param:DrawableRes private val icon: Int) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = size(paint)

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val size = size(paint)
        val center = y + (paint.ascent() + paint.descent()) / 2
        canvas.drawBitmap(MonochromeIcons.bitmap(context, icon, size, paint.color), x, (center - size / 2f).roundToInt().toFloat(), null)
    }

    private fun size(paint: Paint) = (paint.textSize * ICON_TO_TEXT).roundToInt()

    private companion object {
        const val ICON_TO_TEXT = 1.2f
    }
}
