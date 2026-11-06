package io.github.andreygladkov.inkclock.alarm

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun at(text: String, zone: ZoneId = berlin) = ZonedDateTime.of(LocalDateTime.parse(text), zone)

    @Test fun aOneTimeAlarmRingsTodayWhenItsTimeIsStillAhead() {
        assertEquals(at("2026-11-05T07:30"), Alarm(1, 7, 30).nextRing(at("2026-11-05T06:00")))
    }

    @Test fun aOneTimeAlarmWhoseTimeHasPassedRingsTomorrow() {
        assertEquals(at("2026-11-06T07:30"), Alarm(1, 7, 30).nextRing(at("2026-11-05T07:30")))
    }

    @Test fun aRepeatingAlarmSkipsToItsNextDay() {
        val alarm = Alarm(1, 7, 0, days = setOf(MONDAY, WEDNESDAY, FRIDAY))
        assertEquals(at("2026-11-06T07:00"), alarm.nextRing(at("2026-11-05T06:00")))
        assertEquals(at("2026-11-09T07:00"), alarm.nextRing(at("2026-11-06T07:01")))
    }

    @Test fun aWeeklyAlarmOnTodayAfterItsTimeRingsNextWeek() {
        assertEquals(at("2026-11-12T07:00"), Alarm(1, 7, 0, days = setOf(THURSDAY)).nextRing(at("2026-11-05T08:00")))
    }

    @Test fun anAlarmInsideTheSpringGapRingsRightAfterTheClocksJump() {
        assertEquals(at("2026-03-29T03:30"), Alarm(1, 2, 30).nextRing(at("2026-03-29T00:00")))
    }

    @Test fun anAlarmInsideTheAutumnOverlapRingsOnceAtTheEarlierOffset() {
        val ring = Alarm(1, 2, 30).nextRing(at("2026-10-25T00:00"))!!
        assertEquals("+02:00", ring.offset.id)
        val after = Alarm(1, 2, 30).nextRing(ring)!!
        assertEquals(at("2026-10-26T02:30"), after)
    }

    @Test fun aSnoozedAlarmRingsAtTheSnoozeTime() {
        val now = at("2026-11-05T07:00")
        val until = now.plusMinutes(10).toInstant().toEpochMilli()
        assertEquals(now.plusMinutes(10), Alarm(1, 7, 0).snoozed(until).nextRing(now))
    }

    @Test fun aDisabledAlarmNeverRings() {
        assertNull(Alarm(1, 7, 0, isEnabled = false).nextRing(at("2026-11-05T06:00")))
    }

    @Test fun dismissingTurnsOffAOneTimeAlarmAndKeepsARepeatingOne() {
        assertFalse(Alarm(1, 7, 0, snoozedUntil = 1).dismissed().isEnabled)
        val repeating = Alarm(1, 7, 0, days = setOf(SATURDAY), snoozedUntil = 1).dismissed()
        assertTrue(repeating.isEnabled)
        assertNull(repeating.snoozedUntil)
    }

    @Test fun theSchedulerPicksTheEarliestRing() {
        val now = at("2026-11-05T06:00")
        val alarms = listOf(Alarm(1, 9, 0), Alarm(2, 6, 30), Alarm(3, 5, 0, isEnabled = false))
        assertEquals(2, AlarmScheduler.next(alarms, now)?.alarm?.id)
        assertNull(AlarmScheduler.next(emptyList(), now))
    }
}
