package io.github.andreygladkov.inkphone.ui

import android.content.Context
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.eink.InkStyle
import kotlin.math.roundToInt

class InkSegments(
    context: Context,
    @StringRes titles: List<Int>,
    private val onSelect: (Int) -> Unit,
) : LinearLayout(context) {
    private val buttons: List<InkButton>

    init {
        orientation = HORIZONTAL
        val density = resources.displayMetrics.density
        val gap = (GAP_DP * density).roundToInt()
        val vertical = ((InkButton.MIN_HEIGHT_DP - BUTTON_HEIGHT_DP) / 2 * density).roundToInt()
        setPadding(gap, vertical, gap, vertical)
        buttons = titles.mapIndexed { index, title ->
            InkButton(context).apply {
                setText(title)
                textScale = InkStyle.SECONDARY_SCALE
                isFillRounded = true
                minHeight = (BUTTON_HEIGHT_DP * density).roundToInt()
                setPadding(paddingLeft, 0, paddingRight, 0)
                setOnClickListener { select(index) }
            }.also { addView(it, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap }) }
        }
        mark(0)
    }

    private fun select(index: Int) {
        mark(index)
        onSelect(index)
    }

    private fun mark(selected: Int) = buttons.forEachIndexed { index, button ->
        button.isBordered = index == selected
        button.stateDescription = if (index == selected) context.getString(R.string.selected) else null
    }

    private companion object {
        const val GAP_DP = 8f
        const val BUTTON_HEIGHT_DP = 48f
    }
}
