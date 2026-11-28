package io.github.andreygladkov.inkphone.calllog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CallLabelsTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun timeOf(millis: Long): String {
        val labels = CallLabels(ApplicationProvider.getApplicationContext<Context>())
        val entry = CallLogEntry(1, "+33612345678", null, CallType.INCOMING, millis, 0)
        return labels.summary(entry, at(2026, 10, 31, 20)).toString().substringAfter("  ")
    }

    @Config(qualifiers = "ru")
    @Test fun russianDatesUseDots() {
        assertEquals("12.10 09:00", timeOf(at(2026, 10, 12, 9)))
        assertEquals("31.12.25 10:00", timeOf(at(2025, 12, 31, 10)))
    }

    @Config(qualifiers = "fr")
    @Test fun frenchDatesUseSlashes() {
        assertEquals("12/10 09:00", timeOf(at(2026, 10, 12, 9)))
        assertEquals("31/12/25 10:00", timeOf(at(2025, 12, 31, 10)))
    }

    @Config(qualifiers = "en-rUS")
    @Test fun americanTimesFollowTheTwelveHourClock() {
        assertEquals("2:00 PM", timeOf(at(2026, 10, 31, 14)))
        assertEquals("10/12 9:00 AM", timeOf(at(2026, 10, 12, 9)))
    }
}
