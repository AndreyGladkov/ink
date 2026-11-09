package io.github.andreygladkov.ink.ui

import android.content.Context
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

class InkChoiceRow<T>(
    context: Context,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    decorate: InkButton.(T) -> Unit = {},
    isJoined: Boolean = true,
    private val onChoose: (T) -> Unit,
) : LinearLayout(context) {
    private val buttons: List<Pair<T, InkButton>>

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val gap = (GAP_DP * density).roundToInt()
        val sharedBorderOverlap = -(InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
        val spacing = if (isJoined) sharedBorderOverlap else gap
        addView(InkTextView(context).apply {
            text = title
            setPadding(0, gap * 3, 0, gap * 2)
        })
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        buttons = options.mapIndexed { index, (value, label) ->
            val button = InkButton(context).apply {
                text = label
                textScale = InkStyle.SECONDARY_SCALE
                maxLines = 1
                setPadding(gap, paddingTop, gap, paddingBottom)
                isSelected = value == selected
                isJoinedStart = isJoined && index > 0
                isJoinedEnd = isJoined && index < options.lastIndex
                decorate(value)
                setOnClickListener { choose(value) }
            }
            row.addView(button, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = spacing
            })
            value to button
        }
        addView(row)
    }

    private fun choose(value: T) {
        buttons.forEach { (option, button) -> button.isSelected = option == value }
        onChoose(value)
    }

    private companion object {
        const val GAP_DP = 4f
    }
}
