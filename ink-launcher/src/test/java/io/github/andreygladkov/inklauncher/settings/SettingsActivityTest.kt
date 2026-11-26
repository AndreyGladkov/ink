package io.github.andreygladkov.inklauncher.settings

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.LauncherPrefs
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

    @Test fun openingTheOnlyAppFoundIsASettingThatStartsOn() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val row = contentRoot(activity).findOnPages<InkChoiceRow<*>>().single { it.find<InkTextView>().any { view -> view.text.toString() == "Открывать единственное найденное" } }
        assertTrue(row.find<InkButton>().single { it.text.toString() == "Вкл" }.isSelected)
        row.find<InkButton>().single { it.text.toString() == "Выкл" }.performClick()
        assertFalse(context.inkPrefs().getBoolean(LauncherPrefs.OPEN_ONLY_MATCH, true))
    }

    @Test fun openingTheKeyboardAtOnceIsASettingThatStartsOff() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val row = contentRoot(activity).findOnPages<InkChoiceRow<*>>().single { it.find<InkTextView>().any { view -> view.text.toString() == "Сразу открывать клавиатуру" } }
        assertTrue(row.find<InkButton>().single { it.text.toString() == "Выкл" }.isSelected)
        row.find<InkButton>().single { it.text.toString() == "Вкл" }.performClick()
        assertTrue(context.inkPrefs().getBoolean(LauncherPrefs.SEARCH_AT_ONCE, false))
    }

    private fun texts() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        .let { contentRoot(it).findOnPages<InkTextView>().map { view -> view.text.toString() } }

    private companion object {
        const val BADGE = "Значок уведомлений"
    }
}
