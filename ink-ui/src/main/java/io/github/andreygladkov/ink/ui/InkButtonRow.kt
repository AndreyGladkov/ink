package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged

class InkButtonRow(
    context: Context,
    private val gapPx: Int,
    private val maxLabelScale: Float,
    isDivided: Boolean = false,
) : LinearLayout(context) {
    private val buttons = mutableListOf<InkButton>()
    private var fittedFor: List<Any>? = null
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
        addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        buttons += button
        // A button keeps its size when its label changes, so TextView does not ask the row to measure again.
        button.doAfterTextChanged { requestLayout() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        spaceShownButtons()
        fitLabels(MeasureSpec.getSize(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Only a button with a shown one before it gets the gap, so a row with one button left is centred. A start
     * margin set on laid-out params is not resolved into the left margin LinearLayout reads until the params are
     * resolved again, so it is resolved here at once.
     */
    private fun spaceShownButtons() {
        var seenShown = false
        buttons.forEach { button ->
            val params = button.layoutParams as LayoutParams
            val margin = if (seenShown) marginPx else 0
            if (params.marginStart != margin) {
                params.marginStart = margin
                params.resolveLayoutDirection(layoutDirection)
            }
            if (button.visibility != GONE) seenShown = true
        }
    }

    private fun fitLabels(widthPx: Int) {
        val shown = buttons.filter { it.visibility != GONE }
        val key = listOf(widthPx) + shown.map { it.text.toString() }
        if (shown.isEmpty() || widthPx <= 0 || key == fittedFor) return
        fittedFor = key
        val buttonWidth = (widthPx - paddingLeft - paddingRight - betweenPx * (shown.size - 1)) / shown.size
        val scale = fittedLabelScale(shown, buttonWidth, maxLabelScale)
        buttons.forEach { it.textScale = scale }
    }
}
