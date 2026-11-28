package io.github.andreygladkov.ink.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class DatePatternsTest {
    @Test fun flexibleDayPeriodsBecomeAmPm() {
        assertEquals("ah:mm", DatePatterns.withoutFlexibleDayPeriods("Bh:mm"))
        assertEquals("h:mm a", DatePatterns.withoutFlexibleDayPeriods("h:mm BBBB"))
        assertEquals("h:mm a", DatePatterns.withoutFlexibleDayPeriods("h:mm b"))
    }

    @Test fun quotedTextIsLeftAlone() {
        assertEquals("'Bob''s' h:mm a", DatePatterns.withoutFlexibleDayPeriods("'Bob''s' h:mm B"))
    }

    @Test fun traditionalChineseTwelveHourTimeHasNoFlexibleDayPeriod() {
        val pattern = DatePatterns.pattern(Locale.forLanguageTag("zh-TW"), "hm")
        assertFalse('B' in pattern, pattern)
        DatePatterns.formatter(Locale.forLanguageTag("zh-TW"), "hm").format(LocalTime.of(14, 5))
    }
}
