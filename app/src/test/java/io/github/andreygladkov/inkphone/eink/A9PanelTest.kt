package io.github.andreygladkov.inkphone.eink

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.inkPrefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class A9PanelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val app = shadowOf(context as Application)

    private fun clears() = app.broadcastIntents.count { it.action == "EINK_FORCE_CLEAR" && it.`package` == "com.lmqr.ha9_comp_service" }

    private fun afterDelay() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))

    @Test fun nothingIsSentByDefault() {
        A9Panel.onCallScreenClosed(context)
        repeat(10) { A9Panel.onPageFlipped(context) }
        afterDelay()
        assertEquals(0, clears())
    }

    @Test fun clearsAfterCallWhenEnabled() {
        context.inkPrefs().edit { putBoolean(A9Panel.PREF_CLEAR_AFTER_CALL, true) }
        A9Panel.onCallScreenClosed(context)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(0, clears(), "cleared before the panel settled")
        afterDelay()
        assertEquals(1, clears())
    }

    @Test fun clearsEveryNthPageFlip() {
        context.inkPrefs().edit { putInt(A9Panel.PREF_CLEAR_EVERY_PAGES, 3) }
        repeat(7) { A9Panel.onPageFlipped(context) }
        afterDelay()
        assertEquals(2, clears())
    }
}
