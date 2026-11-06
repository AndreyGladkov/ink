package io.github.andreygladkov.inkcalculator.calc

import java.math.BigDecimal

/**
 * What is typed, in keypad order: numbers with [POINT], operators from [OPERATORS], [PERCENT] after a number.
 * After [isResult] a digit starts anew and an operator carries the result on.
 */
data class Entry(val text: String = "", val isResult: Boolean = false, val carry: Carry? = null) {
    /**
     * A result is shown and typed on rounded, but 1 ÷ 3 = × 3 = must still give 1, so its exact value is kept
     * for as long as the text starts with the rounded one.
     */
    data class Carry(val shown: String, val exact: String)

    private val tokens get() = tokenize(text).orEmpty()
    private val number get() = tokens.lastOrNull() as? Num

    val isCalculation: Boolean get() = complete().let { it.size > 1 || it.any { token -> token is Num && token.isPercent } }

    fun evaluate(): Outcome = Arithmetic.evaluate(complete(), carry?.exact?.toBigDecimalOrNull())

    fun digits(keys: String): Entry {
        if (isResult) return Entry().digits(keys)
        val number = number
        if (number != null && (number.isPercent || number.hasExponent)) return this
        val body = number?.body.orEmpty()
        val typed = if (body.isEmpty() || body == "0") keys.trimStart('0').ifEmpty { "0" } else body + keys
        if (typed.trimStart('0', POINT).count(Char::isDigit) > Arithmetic.SHOWN_DIGITS) return this
        return edited(text.dropLast(body.length) + typed)
    }

    fun point(): Entry {
        if (isResult) return Entry().point()
        val number = number
        if (number != null && (number.isPercent || POINT in number.text || number.hasExponent)) return this
        return edited(text + if (number?.body.isNullOrEmpty()) "0$POINT" else "$POINT")
    }

    fun operator(symbol: Char): Entry {
        val last = tokens.lastOrNull()
        val typed = when {
            last == null -> if (symbol == MINUS) "$MINUS" else return this
            last is Num && last.text == "$MINUS" -> if (symbol == MINUS) return this else text.dropLast(2).let { if (it.isEmpty()) it else it + symbol }
            last is Op && symbol == MINUS && last.symbol in "*/" -> text + MINUS
            last is Op -> text.dropLast(1) + symbol
            else -> text + symbol
        }
        return edited(typed)
    }

    fun percent(): Entry {
        val number = number ?: return this
        if (number.isPercent || number.body.isEmpty()) return this
        return edited(text + PERCENT)
    }

    fun erase(): Entry {
        if (isResult) return Entry()
        val number = number
        return edited(if (number != null && number.hasExponent && !number.isPercent) text.dropLast(number.text.length) else text.dropLast(1))
    }

    fun insert(value: String): Entry {
        if (isResult || text.isEmpty()) return Entry(value, isResult = true)
        val number = number
        return edited(when {
            number == null -> text + value
            number.text == "$MINUS" -> if (value.startsWith(MINUS)) text.dropLast(1) + value.drop(1) else text + value
            else -> text.dropLast(number.text.length + if (number.isPercent) 1 else 0) + value
        })
    }

    private fun edited(text: String): Entry {
        if (text.length > MAX_LENGTH) return this
        val carry = carry?.takeIf { text.startsWith(it.shown) && text.getOrNull(it.shown.length).let { next -> next == null || next in OPERATORS || next == PERCENT } }
        return Entry(text, isResult = false, carry = carry)
    }

    private fun complete(): List<Token> = tokenize(text.trimEnd { it in OPERATORS }).orEmpty()

    companion object {
        const val MAX_LENGTH = 60

        fun result(value: BigDecimal): Entry {
            val shown = Arithmetic.shown(value)
            val carry = Carry(shown, value.toString()).takeIf { shown.toBigDecimal().compareTo(value) != 0 }
            return Entry(shown, isResult = true, carry = carry)
        }
    }
}
