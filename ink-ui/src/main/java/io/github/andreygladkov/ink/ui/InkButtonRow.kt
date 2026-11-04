package io.github.andreygladkov.ink.ui

import android.content.Context
import android.widget.LinearLayout

class InkButtonRow(
    context: Context,
    private val gapPx: Int,
    private val maxLabelScale: Float,
) : LinearLayout(context) {
    private val buttons = mutableListOf<InkButton>()
    private var fittedForWidth = -1

    init {
        orientation = HORIZONTAL
    }

    fun addButton(button: InkButton) {
        button.maxLines = 1
        addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (buttons.isNotEmpty()) marginStart = gapPx })
        buttons += button
        fittedForWidth = -1
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        fitLabels(MeasureSpec.getSize(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fitLabels(widthPx: Int) {
        if (buttons.isEmpty() || widthPx <= 0 || widthPx == fittedForWidth) return
        fittedForWidth = widthPx
        val buttonWidth = (widthPx - paddingLeft - paddingRight - gapPx * (buttons.size - 1)) / buttons.size
        val scale = fittedLabelScale(buttons, buttonWidth, maxLabelScale)
        buttons.forEach { it.textScale = scale }
    }
}
