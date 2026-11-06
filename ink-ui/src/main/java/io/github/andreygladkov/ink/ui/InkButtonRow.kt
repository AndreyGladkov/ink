package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.widget.LinearLayout

class InkButtonRow(
    context: Context,
    private val gapPx: Int,
    private val maxLabelScale: Float,
    isDivided: Boolean = false,
) : LinearLayout(context) {
    private val buttons = mutableListOf<InkButton>()
    private var fittedForWidth = -1
    private val betweenPx: Int
    private val marginPx: Int

    init {
        orientation = HORIZONTAL
        if (isDivided) {
            dividerDrawable = InsetDrawable(VerticalLineDrawable(context), gapPx, 0, gapPx, 0)
            showDividers = SHOW_DIVIDER_MIDDLE
        }
        betweenPx = dividerDrawable?.intrinsicWidth ?: gapPx
        marginPx = if (isDivided) 0 else gapPx
    }

    fun addButton(button: InkButton) {
        button.maxLines = 1
        addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (buttons.isNotEmpty()) marginStart = marginPx })
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
        val buttonWidth = (widthPx - paddingLeft - paddingRight - betweenPx * (buttons.size - 1)) / buttons.size
        val scale = fittedLabelScale(buttons, buttonWidth, maxLabelScale)
        buttons.forEach { it.textScale = scale }
    }
}
