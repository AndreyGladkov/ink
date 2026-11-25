package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.InkStyle
import java.time.LocalTime
import kotlin.math.roundToInt

class TimeInput(context: Context, initial: LocalTime, is24Hour: Boolean) : LinearLayout(context) {
    private var entry = TimeEntry.of(initial.hour, initial.minute, is24Hour)
    private val shown = InkTextView(context).apply {
        textScale = TIME_SCALE
        isBold = true
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val error = InkTextView(context).apply {
        setText(R.string.time_invalid)
        textScale = InkStyle.SECONDARY_SCALE
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.INVISIBLE
    }

    init {
        orientation = VERTICAL
        addView(shown, wide())
        addView(error, wide())
        if (!is24Hour) addView(InkChoiceRow(context, context.getString(R.string.half_of_day),
            listOf(false to context.getString(R.string.am), true to context.getString(R.string.pm)), entry.isPm) { pm -> entry = entry.copy(isPm = pm) })
        addView(DigitKeypad(context, onKey = { change(entry.type(it)) }, onErase = { change(entry.erase()) }),
            wide().apply { topMargin = (GAP_DP * resources.displayMetrics.density).roundToInt() })
        show()
    }

    fun time(): LocalTime? {
        val (hour, minute) = entry.toHourMinute() ?: run {
            error.visibility = View.VISIBLE
            return null
        }
        return LocalTime.of(hour, minute)
    }

    private fun change(changed: TimeEntry) {
        entry = changed
        show()
    }

    private fun show() {
        shown.text = entry.display()
        error.visibility = View.INVISIBLE
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private companion object {
        const val TIME_SCALE = 3f
        const val GAP_DP = 8f
    }
}

data class TimeEntry(val entry: DigitEntry, val is24Hour: Boolean, val isPm: Boolean, val isFresh: Boolean) {
    fun type(key: String) = copy(entry = (if (isFresh) entry.copy(digits = "") else entry).type(key), isFresh = false)

    fun erase() = copy(entry = if (isFresh) entry.copy(digits = "") else entry.erase(), isFresh = false)

    fun display(): String = entry.groups().let { (hours, minutes) -> "%02d:%02d".format(hours, minutes) }

    fun toHourMinute(): Pair<Int, Int>? {
        val (hours, minutes) = entry.groups()
        if (minutes > 59) return null
        return when {
            is24Hour -> hours.takeIf { it <= 23 }?.let { it to minutes }
            hours in 1..12 -> (hours % 12 + if (isPm) 12 else 0) to minutes
            else -> null
        }
    }

    companion object {
        fun of(hour: Int, minute: Int, is24Hour: Boolean): TimeEntry {
            val shown = if (is24Hour) hour else (hour % 12).takeIf { it != 0 } ?: 12
            return TimeEntry(DigitEntry.of(4, shown, minute), is24Hour, isPm = hour >= 12, isFresh = true)
        }
    }
}
