package io.github.andreygladkov.inkphone.haptics

import android.content.Context
import android.os.SystemClock
import android.os.VibratorManager
import android.provider.Settings
import android.view.MotionEvent
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.ui.InkButton
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class HapticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Test fun defaultStrengthVibrates() {
        Haptics.of(context).perform(HapticChannel.KEYS)
        assertTrue(shadowOf(vibrator).isVibrating)
        assertEquals(32L, shadowOf(vibrator).milliseconds)
    }

    @Test fun channelsHaveIndependentStrengths() {
        context.inkPrefs().edit {
            putInt(HapticChannel.KEYS.prefKey, 5)
            putInt(HapticChannel.BUTTONS.prefKey, 1)
        }
        Haptics.of(context).perform(HapticChannel.KEYS)
        assertEquals(48L, shadowOf(vibrator).milliseconds)
        Haptics.of(context).perform(HapticChannel.BUTTONS)
        assertEquals(16L, shadowOf(vibrator).milliseconds)
    }

    @Test fun offDoesNotVibrate() {
        context.inkPrefs().edit { putInt(HapticChannel.BUTTONS.prefKey, HapticStrength.OFF) }
        Haptics.of(context).perform(HapticChannel.BUTTONS)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test fun systemTouchFeedbackOffDoesNotVibrate() {
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        val haptics = Haptics.of(context)
        assertTrue(haptics.isDisabledBySystem)
        haptics.perform(HapticChannel.KEYS)
        assertFalse(shadowOf(vibrator).isVibrating)
    }

    @Test fun buttonVibratesOnTouchDownWithItsChannel() {
        context.inkPrefs().edit { putInt(HapticChannel.KEYS.prefKey, 2) }
        val button = InkButton(context).apply { hapticChannel = HapticChannel.KEYS }
        val now = SystemClock.uptimeMillis()
        button.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        assertEquals(24L, shadowOf(vibrator).milliseconds)
    }
}
