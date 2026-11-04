package io.github.andreygladkov.inkphone.dialer

import io.github.andreygladkov.inkphone.contacts.ContactEntry
import io.github.andreygladkov.inkphone.contacts.ContactList

data class Suggestion(val entry: ContactEntry, val number: String)

object T9 {
    private val KEYS = mapOf(
        '2' to "abcабвг", '3' to "defдеёжз", '4' to "ghiийкл", '5' to "jklмноп",
        '6' to "mnoрсту", '7' to "pqrsфхцч", '8' to "tuvшщъы", '9' to "wxyzьэюя",
    )
    private val LETTER_TO_KEY: Map<Char, Char> = KEYS.flatMap { (key, letters) -> letters.map { it to key } }.toMap()
    private const val NAME_FIRST_WORD = 0
    private const val NAME_OTHER_WORD = 1
    private const val NUMBER = 2

    fun lettersFor(key: Char): String = KEYS[key].orEmpty()

    fun encode(word: String): String = word.lowercase().mapNotNull { LETTER_TO_KEY[it] ?: it.takeIf(Char::isDigit) }.joinToString("")

    fun suggest(query: String, entries: List<ContactEntry>): List<Suggestion> {
        val digits = query.filter(Char::isDigit)
        if (digits.isEmpty()) return emptyList()
        val byName = query.all { it in '2'..'9' }
        return entries.mapNotNull { entry -> rank(entry, digits, byName)?.let { (rank, number) -> Triple(rank, entry, number) } }
            .sortedBy { it.first }
            .map { (_, entry, number) -> Suggestion(entry, number) }
    }

    private fun rank(entry: ContactEntry, digits: String, byName: Boolean): Pair<Int, String>? {
        val numbers = entry.contact.numbers
        val firstNumber = numbers.firstOrNull() ?: return null
        if (byName) {
            val words = ContactList.tokens(entry.displayName).map(::encode)
            if (words.firstOrNull()?.startsWith(digits) == true) return NAME_FIRST_WORD to firstNumber
            if (words.drop(1).any { it.startsWith(digits) }) return NAME_OTHER_WORD to firstNumber
        }
        val index = entry.contact.numberDigits.indexOfFirst { it.contains(digits) }
        return if (index >= 0) NUMBER to numbers[index] else null
    }
}
