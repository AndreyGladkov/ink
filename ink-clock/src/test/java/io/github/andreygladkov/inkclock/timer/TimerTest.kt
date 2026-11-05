package io.github.andreygladkov.inkclock.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class TimerTest {
    @Test fun pausingKeepsTheRemainingTimeAndResumingMovesTheEnd() {
        val running = Timer.start(300_000, now = 1_000)
        assertEquals(301_000, running.endsAt)
        val paused = running.pause(now = 101_000)
        assertEquals(200_000, paused.remainingMs)
        assertEquals(Timer.Running(300_000, 700_000), paused.resume(now = 500_000))
    }

    @Test fun aMinuteMoreExtendsBothStates() {
        assertEquals(Timer.Running(360_000, 361_000), Timer.start(300_000, now = 1_000).plus(60_000))
        assertEquals(Timer.Paused(360_000, 90_000), Timer.Paused(300_000, 30_000).plus(60_000))
    }

    @Test fun theScreenRedrawsWhenTheShownValueChanges() {
        assertEquals(5_000, TimerLabels.untilChange(125_000))
        assertEquals(60_000, TimerLabels.untilChange(120_000))
        assertEquals(1_000, TimerLabels.untilChange(61_000))
        assertEquals(1_000, TimerLabels.untilChange(60_000))
        assertEquals(300, TimerLabels.untilChange(42_300))
    }
}
