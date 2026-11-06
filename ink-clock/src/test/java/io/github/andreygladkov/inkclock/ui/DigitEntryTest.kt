package io.github.andreygladkov.inkclock.ui

import io.github.andreygladkov.inkclock.alarm.AlarmTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DigitEntryTest {
    private fun typed(text: String, time: AlarmTime) = text.fold(time) { current, digit -> current.type(digit.toString()) }

    private fun typed(vararg keys: String, maxDigits: Int) = keys.fold(DigitEntry("", maxDigits)) { current, key -> current.type(key) }

    @Test fun digitsShiftInFromTheRight() {
        val entry = typed("7", "3", "0", maxDigits = 4)
        assertEquals(listOf(7, 30), entry.groups())
        assertEquals(listOf(0, 73), entry.erase().groups())
    }

    @Test fun digitsBeyondTheLimitAreIgnored() {
        assertEquals("1234", typed("1", "2", "3", "4", "5", maxDigits = 4).digits)
    }

    @Test fun aDoubleZeroThatDoesNotFitWholeIsIgnored() {
        assertEquals("123", typed("1", "2", "3", "00", maxDigits = 4).digits)
        assertEquals("1200", typed("1", "2", "00", maxDigits = 4).digits)
    }

    @Test fun zerosTypedFirstTakeNoPlace() {
        assertEquals(listOf(1, 0, 0), typed("00", "0", "1", "0", "0", "0", "0", maxDigits = 6).groups())
    }

    @Test fun theFirstKeyReplacesTheShownTime() {
        val time = AlarmTime.of(7, 30, is24Hour = true)
        assertEquals("07:30", time.display())
        assertEquals("00:06", time.type("6").display())
        assertEquals("00:00", time.erase().display())
        assertEquals(6 to 45, typed("645", time).toHourMinute())
    }

    @Test fun impossibleTimesAreRejected() {
        val time = AlarmTime.of(7, 0, is24Hour = true)
        assertNull(typed("2400", time).toHourMinute())
        assertNull(typed("960", time).toHourMinute())
        assertEquals(23 to 59, typed("2359", time).toHourMinute())
    }

    @Test fun twelveHourTimesConvertThroughTheHalfOfDay() {
        val evening = AlarmTime.of(19, 15, is24Hour = false)
        assertEquals("07:15", evening.display())
        assertEquals(19 to 15, evening.toHourMinute())
        assertEquals(0 to 5, typed("1205", AlarmTime.of(9, 0, is24Hour = false)).toHourMinute())
        assertEquals(12 to 5, typed("1205", AlarmTime.of(13, 0, is24Hour = false)).toHourMinute())
        assertNull(typed("1305", AlarmTime.of(9, 0, is24Hour = false)).toHourMinute())
        assertNull(typed("0", AlarmTime.of(9, 0, is24Hour = false)).toHourMinute())
    }
}
