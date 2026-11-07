package io.github.andreygladkov.inkcalculator.calc

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs

data class TapeLine(val expression: String, val result: String)

class CalculatorStore(private val prefs: SharedPreferences) {
    var entry: Entry
        get() {
            val shown = prefs.getString(CARRY_SHOWN, null)
            val exact = prefs.getString(CARRY_EXACT, null)
            return Entry(
                text = prefs.getString(TEXT, "").orEmpty(),
                isResult = prefs.getBoolean(IS_RESULT, false),
                carry = if (shown != null && exact != null) Entry.Carry(shown, exact) else null,
            )
        }
        set(value) = prefs.edit {
            putString(TEXT, value.text)
            putBoolean(IS_RESULT, value.isResult)
            putString(CARRY_SHOWN, value.carry?.shown)
            putString(CARRY_EXACT, value.carry?.exact)
        }

    fun tape(): List<TapeLine> = prefs.getString(TAPE, "").orEmpty().lines().filter { it.isNotEmpty() }.map { line ->
        TapeLine(line.substringBefore(FIELD), line.substringAfter(FIELD))
    }

    fun remember(line: TapeLine) {
        val lines = (tape() + line).takeLast(TAPE_SIZE)
        prefs.edit { putString(TAPE, lines.joinToString("\n") { "${it.expression}$FIELD${it.result}" }) }
    }

    companion object {
        private const val TEXT = "entry_text"
        private const val IS_RESULT = "entry_is_result"
        private const val CARRY_SHOWN = "entry_carry_shown"
        private const val CARRY_EXACT = "entry_carry_exact"
        private const val TAPE = "tape"
        private const val FIELD = '\t'
        private const val TAPE_SIZE = 6

        fun of(context: Context) = CalculatorStore(context.inkPrefs())
    }
}
