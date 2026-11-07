package io.github.andreygladkov.inklauncher.home

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class GraphemesTest {
    @Test fun emojiAreKeptWholeWhenTextIsCut() {
        assertEquals("A😀B😀", "A😀B😀C".takeGraphemes(4))
        assertEquals("👨‍👩‍👧", "👨‍👩‍👧👍".takeGraphemes(1))
        assertEquals("Ok", "Ok".takeGraphemes(4))
        assertEquals("", "".takeGraphemes(1))
    }
}
