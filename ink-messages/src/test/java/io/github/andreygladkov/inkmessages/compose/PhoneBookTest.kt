package io.github.andreygladkov.inkmessages.compose

import io.github.andreygladkov.inkmessages.contacts.PhoneBook
import io.github.andreygladkov.inkmessages.contacts.PhoneEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneBookTest {
    private val entries = listOf(
        PhoneEntry("Алёна Петрова", "+7 900 111-22-33", "Моб.", isStarred = false),
        PhoneEntry("Мама", "+7 900 444-55-66", "Моб.", isStarred = true),
    )

    @Test fun emptyQueryPutsFavoritesFirst() {
        assertEquals(listOf("Мама", "Алёна Петрова"), PhoneBook.search(entries, "").map { it.name })
    }

    @Test fun searchesNamesAndDigits() {
        assertEquals(listOf("Алёна Петрова"), PhoneBook.search(entries, "пет але").map { it.name })
        assertEquals(listOf("Мама"), PhoneBook.search(entries, "4445").map { it.name })
    }

    @Test fun typedNumberNeedsThreeDigitsAndNoLetters() {
        assertEquals("+7 (900) 123", PhoneBook.typedNumber(" +7 (900) 123 "))
        assertNull(PhoneBook.typedNumber("90"))
        assertNull(PhoneBook.typedNumber("мама 900"))
    }
}
