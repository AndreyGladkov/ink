package io.github.andreygladkov.inkphone.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.dialer.T9
import io.github.andreygladkov.inkphone.haptics.HapticChannel
import kotlin.math.roundToInt

class DialPad(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onKeyDown(key: Char) {}
        fun onKeyUp(key: Char) {}
        fun onKeyLongPress(key: Char): Boolean = false
    }

    init {
        orientation = VERTICAL
        val gap = (GAP_DP * resources.displayMetrics.density).roundToInt()
        ROWS.forEachIndexed { rowIndex, row ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                row.forEachIndexed { index, key ->
                    addView(key(key), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { if (index > 0) marginStart = gap })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { if (rowIndex > 0) topMargin = gap })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun key(key: Char) = InkButton(context).apply {
        hapticChannel = HapticChannel.KEYS
        text = label(key)
        contentDescription = key.toString()
        setPadding(0, 0, 0, 0)
        minHeight = 0
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> listener.onKeyDown(key)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> listener.onKeyUp(key)
            }
            false
        }
        setOnLongClickListener { listener.onKeyLongPress(key) }
    }

    private fun label(key: Char): CharSequence {
        val letters = when (key) {
            '0' -> "+"
            else -> T9.lettersFor(key).let { all ->
                val latin = all.filter { it in 'a'..'z' }.uppercase()
                val cyrillic = all.filter { it !in 'a'..'z' && it != 'ё' }.uppercase()
                listOf(latin, cyrillic).filter { it.isNotEmpty() }.joinToString(" ")
            }
        }
        val text = SpannableStringBuilder(key.toString())
        text.setSpan(RelativeSizeSpan(DIGIT_SCALE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append('\n')
        val start = text.length
        text.append(letters.ifEmpty { " " })
        text.setSpan(RelativeSizeSpan(LETTERS_SCALE), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private companion object {
        const val GAP_DP = 6f
        const val DIGIT_SCALE = 1.5f
        const val LETTERS_SCALE = 0.6f
        val ROWS = listOf("123", "456", "789", "*0#")
    }
}
