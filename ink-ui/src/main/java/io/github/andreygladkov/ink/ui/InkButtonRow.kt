package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

class InkButtonRow(
    context: Context,
    private val gapPx: Int,
    private val maxLabelScale: Float,
    private val isDivided: Boolean = false,
) : LinearLayout(context) {
    private val buttons = mutableListOf<InkButton>()
    private var fittedForWidth = -1

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun addButton(button: InkButton) {
        button.maxLines = 1
        if (isDivided && buttons.isNotEmpty()) {
            addView(VerticalDivider(context), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = gapPx })
        }
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
        val between = if (isDivided) gapPx * 2 + VerticalDivider.widthPx(context) else gapPx
        val buttonWidth = (widthPx - paddingLeft - paddingRight - between * (buttons.size - 1)) / buttons.size
        val scale = fittedLabelScale(buttons, buttonWidth, maxLabelScale)
        buttons.forEach { it.textScale = scale }
    }
}
