package io.github.andreygladkov.inkmessages

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MessageDatesLocaleTest {
    private val now = LocalDate.of(2026, 11, 4).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val lastYear = LocalDate.of(2025, 11, 3).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dates() = MessageDates.of(ApplicationProvider.getApplicationContext<Context>())

    @Config(qualifiers = "ru")
    @Test fun russianOlderDatesUseDots() {
        assertEquals("03.11.25", dates().listLabel(lastYear, now))
    }

    @Config(qualifiers = "fr")
    @Test fun frenchOlderDatesUseSlashes() {
        assertEquals("03/11/25", dates().listLabel(lastYear, now))
    }

    @Config(qualifiers = "en-rUS")
    @Test fun americanTimesFollowTheTwelveHourClock() {
        assertEquals("9:05 AM", dates().listLabel(LocalDate.of(2026, 11, 4).atTime(9, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), now))
    }
}
