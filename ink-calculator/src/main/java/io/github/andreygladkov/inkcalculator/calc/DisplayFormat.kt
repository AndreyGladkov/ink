package io.github.andreygladkov.inkcalculator.calc

import java.text.DecimalFormatSymbols
import java.util.Locale

class DisplayFormat(val decimalSeparator: Char, private val groupingSeparator: Char) {
    fun expression(text: String): String = tokenize(text).orEmpty().joinToString("") { token ->
        when (token) {
            is Op -> " ${symbol(token.symbol)} "
            is Num -> number(token.text) + if (token.isPercent) "$PERCENT" else ""
        }
    }

    fun copyable(text: String): String = text.replace(POINT, decimalSeparator)

    fun number(text: String): String {
        val sign = if (text.startsWith(MINUS)) SIGN else ""
        val mantissa = text.removePrefix("$MINUS").substringBefore(EXPONENT)
        val exponent = text.substringAfter(EXPONENT, "").replace(MINUS, SIGN.single())
        val integer = mantissa.substringBefore(POINT)
        val fraction = if (POINT in mantissa) decimalSeparator + mantissa.substringAfter(POINT) else ""
        val digits = integer.reversed().chunked(GROUP).joinToString("$groupingSeparator").reversed()
        return sign + digits + fraction + if (exponent.isEmpty()) "" else "e$exponent"
    }

    companion object {
        private const val SIGN = "−"
        private const val GROUP = 3
        private const val NO_BREAK_SPACE = '\u00A0'

        fun symbol(operator: Char) = when (operator) {
            '+' -> "+"
            '-' -> SIGN
            '*' -> "×"
            '/' -> "÷"
            else -> operator.toString()
        }

        fun of(locale: Locale): DisplayFormat {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val grouping = symbols.groupingSeparator.takeUnless { it.isWhitespace() } ?: NO_BREAK_SPACE
            return DisplayFormat(symbols.decimalSeparator, grouping)
        }
    }
}
