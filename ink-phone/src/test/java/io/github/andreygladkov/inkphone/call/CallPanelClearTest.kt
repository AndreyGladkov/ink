package io.github.andreygladkov.inkphone.call

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.inkPrefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CallPanelClearTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun clearsAfterDelay(): Int {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        return shadowOf(context as Application).broadcastIntents.count { it.action == "EINK_FORCE_CLEAR" }
    }

    @Test fun nothingIsSentByDefault() {
        CallPanelClear.onCallScreenClosed(context)
        assertEquals(0, clearsAfterDelay())
    }

    @Test fun clearsAfterCallWhenEnabled() {
        context.inkPrefs().edit { putBoolean(CallPanelClear.PREF_CLEAR_AFTER_CALL, true) }
        CallPanelClear.onCallScreenClosed(context)
        assertEquals(1, clearsAfterDelay())
    }
}
