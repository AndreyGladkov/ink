package io.github.andreygladkov.ink

import android.app.Application
import android.os.UserManager
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class InkPrefsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun beforeTheFirstUnlockPreferencesComeFromDeviceStorageWithDefaults() {
        context.inkPrefs().edit(commit = true) { putBoolean("style_inverted", true) }
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(false)
        assertTrue(context.lockSafeStorage().isDeviceProtectedStorage)
        assertFalse(context.inkPrefs().getBoolean("style_inverted", false))
        shadowOf(context.getSystemService(UserManager::class.java)).setUserUnlocked(true)
        assertFalse(context.lockSafeStorage().isDeviceProtectedStorage)
        assertTrue(context.inkPrefs().getBoolean("style_inverted", false))
    }
}
