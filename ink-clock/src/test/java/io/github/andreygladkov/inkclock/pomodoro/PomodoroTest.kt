package io.github.andreygladkov.inkclock.pomodoro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PomodoroTest {
    private val plan = PomodoroPlan()

    @Test fun finishedWorkCountsAndLeadsToAShortBreakThatWaitsForATap() {
        val next = Pomodoro().start(now = 0).finish(plan, today = 10)
        assertEquals(Pomodoro(Phase.SHORT_BREAK, startedAt = null, cycleDone = 1, doneToday = 1, day = 10), next)
        assertEquals(Pomodoro(Phase.WORK, cycleDone = 1, doneToday = 1, day = 10), next.start(now = 0).finish(plan, today = 10))
    }

    @Test fun theLastWorkOfACycleLeadsToALongBreakThatStartsANewCycle() {
        val fourth = Pomodoro(cycleDone = 3, doneToday = 3, day = 10).start(now = 0).finish(plan, today = 10)
        assertEquals(Phase.LONG_BREAK, fourth.phase)
        assertEquals(4, fourth.doneToday)
        assertEquals(Pomodoro(Phase.WORK, cycleDone = 0, doneToday = 4, day = 10), fourth.start(now = 0).finish(plan, today = 10))
    }

    @Test fun theDailyCountStartsOverOnANewDay() {
        val yesterday = Pomodoro(cycleDone = 1, doneToday = 6, day = 10)
        assertEquals(0, yesterday.doneOn(11))
        assertEquals(1, yesterday.start(now = 0).finish(plan, today = 11).doneToday)
    }

    @Test fun aBreakCanBeSkippedButWorkCannot() {
        assertEquals(Pomodoro(Phase.WORK, cycleDone = 0), Pomodoro(Phase.LONG_BREAK, startedAt = 5, cycleDone = 4).skipBreak())
        assertEquals(Pomodoro(Phase.WORK, startedAt = 5), Pomodoro(Phase.WORK, startedAt = 5).skipBreak())
    }

    @Test fun aPhaseEndsAfterItsPlannedMinutes() {
        assertEquals(25 * 60_000L + 7, Pomodoro().start(now = 7).endsAt(plan))
        assertEquals(15 * 60_000L, Pomodoro(Phase.LONG_BREAK).start(now = 0).endsAt(plan))
        assertNull(Pomodoro().endsAt(plan))
    }

    @Test fun workEatsTheTomatoClockwiseFromTheStemOneMinuteAtATime() {
        val dial = PomodoroDial.of(Pomodoro().start(now = 0), plan, now = 3 * 60_000L + 5)
        assertEquals(PomodoroDial(25, black = 22, refills = false), dial)
        assertEquals(-90f + 43.2f, dial.startDegrees, 0.001f)
        assertEquals(316.8f, dial.sweepDegrees, 0.001f)
        assertEquals(PomodoroDial(25, black = 25, refills = false), PomodoroDial.of(Pomodoro(), plan, now = 0))
    }

    @Test fun aBreakFillsTheTomatoBackFromTheStem() {
        val dial = PomodoroDial.of(Pomodoro(Phase.SHORT_BREAK).start(now = 0), plan, now = 2 * 60_000L)
        assertEquals(PomodoroDial(5, black = 2, refills = true), dial)
        assertEquals(-90f, dial.startDegrees)
        assertEquals(144f, dial.sweepDegrees, 0.001f)
        assertEquals(0, PomodoroDial.of(Pomodoro(Phase.SHORT_BREAK), plan, now = 0).black)
    }
}
