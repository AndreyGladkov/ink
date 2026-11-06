package io.github.andreygladkov.inkcalculator.ui

import android.content.Context
import android.graphics.Paint
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.textWidthAtScale

/** Takes the largest of [scales] at which the text fits on one line, and wraps at the smallest. */
class FitTextView(context: Context, private val scales: List<Float>) : InkTextView(context) {
    private val probe = Paint()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            probe.set(paint)
            val scale = scales.firstOrNull { textWidthAtScale(text, it, probe) <= width } ?: scales.last()
            if (scale != textScale) textScale = scale
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    // TextView measures again only when the new text changes its height, which a line that only got longer does not.
    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        requestLayout()
    }
}
