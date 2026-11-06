package io.github.andreygladkov.inkclock.ui

import android.content.Context
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkclock.R
import kotlin.math.roundToInt

class DigitKeypad(
    context: Context,
    private val onDigit: (Char) -> Unit,
    private val onErase: () -> Unit,
    hasDoubleZero: Boolean = false,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val gap = (GAP_DP * density).roundToInt()
        val rows = DIGIT_ROWS + "${if (hasDoubleZero) DOUBLE_ZERO else BLANK}0$ERASE"
        rows.forEachIndexed { rowIndex, row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEachIndexed { index, key ->
                    addView(key(key), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
        }
    }

    private fun key(key: Char) = InkButton(context).apply {
        hapticChannel = HapticChannel.KEYS
        when (key) {
            ERASE -> {
                icon = R.drawable.ic_backspace
                contentDescription = context.getString(R.string.erase)
                setOnClickListener { onErase() }
            }
            BLANK -> {
                isBordered = false
                isEnabled = false
            }
            DOUBLE_ZERO -> {
                text = "00"
                textScale = InkStyle.TITLE_SCALE
                setOnClickListener { repeat(2) { onDigit('0') } }
            }
            else -> {
                text = key.toString()
                textScale = InkStyle.TITLE_SCALE
                setOnClickListener { onDigit(key) }
            }
        }
    }

    private companion object {
        const val GAP_DP = 6f
        const val ERASE = '<'
        const val BLANK = ' '
        const val DOUBLE_ZERO = 'Z'
        val DIGIT_ROWS = listOf("123", "456", "789")
    }
}
