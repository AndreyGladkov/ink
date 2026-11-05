package io.github.andreygladkov.inkclock.stopwatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopwatchTest {
    @Test fun elapsedTimeAddsUpAcrossPauses() {
        val stopwatch = Stopwatch().start(now = 1_000).pause(now = 6_000).start(now = 100_000)
        assertEquals(8_000, stopwatch.elapsed(now = 103_000))
        assertTrue(Stopwatch().isIdle)
    }

    @Test fun lapsAreMarkedAtTheTotalAndMeasuredFromTheLastOne() {
        val stopwatch = Stopwatch().start(now = 0).lap(now = 61_200).lap(now = 90_000).pause(now = 95_000).lap(now = 99_000)
        assertEquals(listOf(61_200L, 90_000L), stopwatch.laps)
        assertEquals(listOf(61_200L, 28_800L), stopwatch.lapLengths())
    }

    @Test fun elapsedTimeIsFormattedWithHoursOnlyWhenNeeded() {
        assertEquals("01:01", StopwatchLabels.elapsed(61_250, tenths = false))
        assertEquals("01:01.2", StopwatchLabels.elapsed(61_250, tenths = true))
        assertEquals("1:00:05", StopwatchLabels.elapsed(3_605_000, tenths = false))
    }
}
