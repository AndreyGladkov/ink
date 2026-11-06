package io.github.andreygladkov.inkcalculator.ui

import android.content.Context
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkcalculator.R
import io.github.andreygladkov.inkcalculator.calc.DisplayFormat
import kotlin.math.roundToInt

sealed interface Key {
    data class Digits(val keys: String) : Key
    data class Operator(val symbol: Char) : Key
    data object Point : Key
    data object Percent : Key
    data object Erase : Key
    data object Clear : Key
    data object Equals : Key
}

class CalculatorKeypad(context: Context, private val decimalSeparator: Char, private val onKey: (Key) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val gap = (GAP_DP * density).roundToInt()
        val height = (KEY_HEIGHT_DP * density).roundToInt()
        ROWS.forEachIndexed { rowIndex, row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
                row.forEachIndexed { index, key ->
                    addView(button(key).apply { minHeight = height }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { if (index > 0) marginStart = gap })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { if (rowIndex > 0) topMargin = gap })
        }
    }

    private fun button(key: Key) = InkButton(context).apply {
        hapticChannel = HapticChannel.KEYS
        textScale = InkStyle.TITLE_SCALE
        when (key) {
            is Key.Digits -> text = key.keys
            is Key.Operator -> {
                text = DisplayFormat.symbol(key.symbol)
                textScale = SIGN_SCALE
            }
            Key.Point -> {
                text = decimalSeparator.toString()
                textScale = SIGN_SCALE
            }
            Key.Percent -> text = "%"
            Key.Clear -> text = "C"
            Key.Equals -> {
                text = "="
                textScale = SIGN_SCALE
                isSelected = true
            }
            Key.Erase -> {
                icon = R.drawable.ic_backspace
                contentDescription = context.getString(R.string.erase)
            }
        }
        setOnClickListener { onKey(key) }
    }

    private companion object {
        const val GAP_DP = 6f
        const val KEY_HEIGHT_DP = 64f
        // Roboto draws operator signs and the decimal separator small next to digits; this brings them to the digits' size.
        const val SIGN_SCALE = 1.7f
        val ROWS = listOf(
            listOf(Key.Clear, Key.Erase, Key.Percent, Key.Operator('/')),
            listOf(Key.Digits("7"), Key.Digits("8"), Key.Digits("9"), Key.Operator('*')),
            listOf(Key.Digits("4"), Key.Digits("5"), Key.Digits("6"), Key.Operator('-')),
            listOf(Key.Digits("1"), Key.Digits("2"), Key.Digits("3"), Key.Operator('+')),
            listOf(Key.Digits("00"), Key.Digits("0"), Key.Point, Key.Equals),
        )
    }
}
