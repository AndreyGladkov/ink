package io.github.andreygladkov.inklauncher.notify

import android.app.Application
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inklauncher.LauncherPrefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NotificationBadgeTest {
    private val prefs = ApplicationProvider.getApplicationContext<Application>().inkPrefs()

    @Test fun eachBadgeShowsNothingWithoutNotifications() {
        NotificationBadge.entries.forEach { assertEquals("", it.label(0), "$it") }
    }

    @Test fun aNumberCountsADotOnlyMarksAndNoneHides() {
        assertEquals("12", NotificationBadge.NUMBER.label(12))
        assertEquals("●", NotificationBadge.DOT.label(12))
        assertEquals("", NotificationBadge.NONE.label(12))
    }

    @Test fun theSavedBadgeIsReadBack() {
        NotificationBadge.entries.forEach {
            NotificationBadge.save(prefs, it)
            assertEquals(it, NotificationBadge.of(prefs))
        }
    }

    @Test fun aNumberIsShownUntilChosenOrWhenTheSavedChoiceIsUnknown() {
        assertEquals(NotificationBadge.NUMBER, NotificationBadge.of(prefs))
        prefs.edit { putString(LauncherPrefs.NOTIFICATION_BADGE, "STAR") }
        assertEquals(NotificationBadge.NUMBER, NotificationBadge.of(prefs))
    }
}
