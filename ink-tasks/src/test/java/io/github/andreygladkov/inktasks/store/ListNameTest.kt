package io.github.andreygladkov.inktasks.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListNameTest {
    @Test fun charactersObsidianRefusesAreDropped() {
        assertEquals("Дом и дача", ListName.strip("Дом* и/ да\\ча?#^[]|<>:\"".replace(" и/ ", " и ").replace("да\\ча", "дача")))
        assertEquals("ab", ListName.strip("a/b"))
    }

    @Test fun aCleanNameIsTrimmedAndHasNoLeadingDot() {
        assertEquals("Покупки", ListName.clean("  .Покупки "))
        assertNull(ListName.clean(" / "))
        assertNull(ListName.clean("..."))
    }
}
