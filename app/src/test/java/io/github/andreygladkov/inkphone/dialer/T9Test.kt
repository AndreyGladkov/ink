package io.github.andreygladkov.inkphone.dialer

import io.github.andreygladkov.inkphone.contacts.AccountRef
import io.github.andreygladkov.inkphone.contacts.ContactList
import io.github.andreygladkov.inkphone.contacts.ContactListOptions
import io.github.andreygladkov.inkphone.contacts.ContactSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class T9Test {
    private fun contact(id: Long, name: String, vararg numbers: String) =
        ContactSummary(id, "k$id", name, name, false, true, numbers.toList(), setOf(AccountRef("com.google", "a")))

    private val entries = ContactList.build(
        listOf(
            contact(1, "Иван Петров", "+7 701 111 22 33"),
            contact(2, "Пётр Иванов", "+7 702 222 33 44"),
            contact(3, "Anna Smith", "+1 555 0100"),
            contact(4, "Мама", "+7 777 000 11 22", "+7 700 999 88 77"),
        ),
        ContactListOptions(),
    )

    private fun names(query: String) = T9.suggest(query, entries).map { it.entry.displayName }

    @Test fun encodesRussianAndLatinLetters() {
        assertEquals("4225", T9.encode("Иван"))
        assertEquals("2662", T9.encode("Anna"))
        assertEquals("3", T9.encode("ё"))
        assertEquals("9", T9.encode("я"))
    }

    @Test fun firstWordMatchesRankAboveOtherWords() {
        assertEquals(listOf("Иван Петров", "Пётр Иванов"), names("42"))
        assertEquals(listOf("Пётр Иванов", "Иван Петров"), names("536"))
    }

    @Test fun matchesLatinNames() {
        assertEquals(listOf("Anna Smith"), names("2662"))
    }

    @Test fun matchesNumbersAndPicksMatchingNumber() {
        val suggestion = T9.suggest("99988", entries).single()
        assertEquals("Мама", suggestion.entry.displayName)
        assertEquals("+7 700 999 88 77", suggestion.number)
    }

    @Test fun zeroOneAndSymbolsSearchOnlyNumbers() {
        assertEquals(listOf("Anna Smith"), names("+1555"))
        assertEquals(emptyList(), names(""))
    }

    @Test fun lettersForKeys() {
        assertEquals("abcабвг", T9.lettersFor('2'))
        assertEquals("", T9.lettersFor('1'))
    }
}
