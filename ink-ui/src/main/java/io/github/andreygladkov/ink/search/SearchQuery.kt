package io.github.andreygladkov.ink.search

import java.text.Normalizer
import java.util.Locale

/** Matches word prefixes in any order, ignoring case, diacritics and ё/е; a query of digits alone also matches inside numbers. */
class SearchQuery(text: String) {
    val tokens: List<String> = tokens(text)
    private val digits = text.filter(Char::isDigit)
    private val isPhoneQuery = text.none(Char::isLetter) && digits.length >= MIN_PHONE_QUERY_DIGITS

    val isEmpty: Boolean get() = tokens.isEmpty()

    fun matches(nameTokens: List<String>, numberDigits: List<String> = emptyList()): Boolean =
        tokens.all { query -> nameTokens.any { it.startsWith(query) } } ||
            isPhoneQuery && numberDigits.any { it.contains(digits) }

    companion object {
        private const val MIN_PHONE_QUERY_DIGITS = 2
        private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        fun tokens(text: String): List<String> = normalize(text).split(TOKEN_SEPARATOR).filter { it.isNotEmpty() }

        fun stripMarks(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

        private fun normalize(text: String): String = stripMarks(text.lowercase(Locale.ROOT).replace('ё', 'е'))
    }
}
