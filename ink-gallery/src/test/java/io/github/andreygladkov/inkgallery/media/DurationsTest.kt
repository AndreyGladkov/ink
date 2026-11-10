package io.github.andreygladkov.inkgallery.media

import kotlin.test.Test
import kotlin.test.assertEquals

class DurationsTest {
    @Test fun minutesAndSecondsUntilAnHour() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9_999))
        assertEquals("1:30", formatDuration(90_000))
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test fun hoursOnceThereAreAny() {
        assertEquals("1:02:05", formatDuration(3_725_000))
    }
}
