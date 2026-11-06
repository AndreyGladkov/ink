package io.github.andreygladkov.inkcalculator.calc

sealed interface Token

data class Num(val text: String, val isPercent: Boolean = false) : Token {
    val hasExponent get() = EXPONENT in text
    val body get() = text.removePrefix(MINUS.toString())
}

data class Op(val symbol: Char) : Token

const val OPERATORS = "+-*/"
const val MINUS = '-'
const val PERCENT = '%'
const val POINT = '.'
const val EXPONENT = 'E'

/**
 * The text alternates numbers and operators; a minus where a number is expected is that number's sign, and a minus
 * right after the exponent mark belongs to the exponent. Returns null for text the keypad cannot produce.
 */
fun tokenize(text: String): List<Token>? {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < text.length) {
        if (tokens.lastOrNull() is Num) {
            if (text[i] !in OPERATORS) return null
            tokens += Op(text[i++])
            continue
        }
        val start = i
        if (text[i] == MINUS) i++
        while (i < text.length && isNumberChar(text, i)) i++
        val isPercent = i < text.length && text[i] == PERCENT
        if (i == start && !isPercent) return null
        tokens += Num(text.substring(start, i), isPercent)
        if (isPercent) i++
    }
    return tokens
}

private fun isNumberChar(text: String, i: Int) =
    text[i].isDigit() || text[i] == POINT || text[i] == EXPONENT || (text[i] == MINUS && text[i - 1] == EXPONENT)
