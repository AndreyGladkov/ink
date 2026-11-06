package io.github.andreygladkov.inkclock.ui

import android.content.Context
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkclock.R
import kotlin.math.roundToInt

class DigitKeypad(context: Context, private val onKey: (String) -> Unit, private val onErase: () -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val gap = (GAP_DP * density).roundToInt()
        ROWS.forEachIndexed { rowIndex, row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEachIndexed { index, key ->
                    addView(key(key), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
        }
    }

    private fun key(key: String) = InkButton(context).apply {
        hapticChannel = HapticChannel.KEYS
        when (key) {
            ERASE -> {
                icon = R.drawable.ic_backspace
                contentDescription = context.getString(R.string.erase)
                setOnClickListener { onErase() }
            }
            else -> {
                text = key
                textScale = InkStyle.TITLE_SCALE
                setOnClickListener { onKey(key) }
            }
        }
    }

    private companion object {
        const val GAP_DP = 6f
        const val ERASE = "<"
        val ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", ERASE))
    }
}
