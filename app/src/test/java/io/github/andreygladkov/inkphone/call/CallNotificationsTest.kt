package io.github.andreygladkov.inkphone.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CallNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun answerOpensCallScreenDirectly() {
        val pending = shadowOf(CallNotifications.answerIntent(context, "call7"))
        assertTrue(pending.isActivityIntent)
        assertEquals(CallActivity::class.java.name, pending.savedIntent.component?.className)
        assertEquals("call7", pending.savedIntent.getStringExtra("answer"))
    }
}
