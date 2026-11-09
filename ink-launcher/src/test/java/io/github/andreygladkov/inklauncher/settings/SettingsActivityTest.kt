package io.github.andreygladkov.inklauncher.settings

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.notify.InkNotificationListener
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun theNotificationBadgeIsOfferedOnlyOnceTheLauncherSeesNotifications() {
        assertFalse(BADGE in texts())
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationListenerAccessGranted(ComponentName(context, InkNotificationListener::class.java), true)
        assertTrue(BADGE in texts())
    }

    private fun texts() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        .let { contentRoot(it).find<InkTextView>().map { view -> view.text.toString() } }

    private companion object {
        const val BADGE = "Значок уведомлений"
    }
}
