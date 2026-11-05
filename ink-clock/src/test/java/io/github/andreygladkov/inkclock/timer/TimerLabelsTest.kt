package io.github.andreygladkov.inkclock.timer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class TimerLabelsTest {
    private val labels = TimerLabels(ApplicationProvider.getApplicationContext<Application>())

    @Test fun aboveAMinuteOnlyWholeMinutesAreShownRoundedUp() {
        assertEquals("13 мин", labels.remaining(12 * 60_000 + 1))
        assertEquals("1 ч 5 мин", labels.remaining(65 * 60_000))
        assertEquals("2 ч", labels.remaining(120 * 60_000))
        assertEquals("1 мин", labels.remaining(60_000))
        assertEquals("45 с", labels.remaining(44_100))
    }

    @Test fun durationsNameEveryPart() {
        assertEquals("1 ч 30 мин", labels.duration(90 * 60_000))
        assertEquals("1 мин 30 с", labels.duration(90_000))
        assertEquals("0 с", labels.duration(0))
    }
}
