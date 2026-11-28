package io.github.andreygladkov.inktasks.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class DueLabelsTest {
    private val today = LocalDate.of(2026, 11, 9)
    private fun labels() = DueLabels(ApplicationProvider.getApplicationContext<Context>(), today)

    @Config(qualifiers = "ru")
    @Test fun russianDatesPutTheDayFirst() {
        assertEquals("5 мар.", labels().day(LocalDate.of(2026, 3, 5)))
        assertEquals("5 мар. 2025\u202Fг.", labels().day(LocalDate.of(2025, 3, 5)))
    }

    @Config(qualifiers = "de")
    @Test fun germanDatesFollowTheDayWithAPeriod() {
        assertEquals("5. März", labels().day(LocalDate.of(2026, 3, 5)))
        assertEquals("5. März 2025", labels().day(LocalDate.of(2025, 3, 5)))
    }

    @Config(qualifiers = "en-rUS")
    @Test fun americanDatesPutTheMonthFirst() {
        assertEquals("Mar 5", labels().day(LocalDate.of(2026, 3, 5)))
        assertEquals("Mar 5, 2025", labels().day(LocalDate.of(2025, 3, 5)))
    }

    @Config(qualifiers = "en-rUS")
    @Test fun midSentenceLowersOnlyRelativeDays() {
        assertEquals("today", labels().dayInSentence(today))
        assertEquals("yesterday", labels().dayInSentence(today.minusDays(1)))
        assertEquals("Mar 5", labels().dayInSentence(LocalDate.of(2026, 3, 5)))
    }
}
