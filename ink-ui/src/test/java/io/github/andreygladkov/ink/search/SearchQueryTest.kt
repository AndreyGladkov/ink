package io.github.andreygladkov.ink.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchQueryTest {
    private val name = SearchQuery.tokens("Алёна Петрова-Водкина")

    @Test fun wordPrefixesMatchInAnyOrder() {
        assertTrue(SearchQuery("вод ал").matches(name))
        assertFalse(SearchQuery("лена").matches(name))
    }

    @Test fun yoAndDiacriticsAreIgnored() {
        assertTrue(SearchQuery("АЛЕНА").matches(name))
        assertTrue(SearchQuery("jose").matches(SearchQuery.tokens("José")))
    }

    @Test fun digitsMatchInsideNumbersOnlyWithoutLetters() {
        assertTrue(SearchQuery("+7 (900) 11").matches(name, listOf("79001112233")))
        assertFalse(SearchQuery("а 900").matches(name, listOf("79001112233")))
        assertFalse(SearchQuery("9").matches(emptyList(), listOf("79001112233")))
    }

    @Test fun blankQueryIsEmpty() {
        assertTrue(SearchQuery(" - ").isEmpty)
        assertEquals(listOf("a", "b"), SearchQuery("A, b").tokens)
    }
}
