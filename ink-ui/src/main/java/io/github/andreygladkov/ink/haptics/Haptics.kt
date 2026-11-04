package io.github.andreygladkov.ink.haptics

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import io.github.andreygladkov.ink.inkPrefs

enum class HapticChannel(val prefKey: String) {
    KEYS("haptic_keys"),
    BUTTONS("haptic_buttons"),
}

data class HapticPulse(val durationMs: Long, val amplitude: Int)

object HapticStrength {
    const val OFF = 0
    const val MAX = 5
    const val DEFAULT = 3
    private const val AMPLITUDE_PULSE_MS = 20L
    private const val MIN_DURATION_MS = 16L
    private const val DURATION_STEP_MS = 8L
    private const val MAX_AMPLITUDE = 255

    fun pulse(strength: Int, hasAmplitudeControl: Boolean): HapticPulse? {
        val level = strength.coerceAtMost(MAX)
        return when {
            level <= OFF -> null
            hasAmplitudeControl -> HapticPulse(AMPLITUDE_PULSE_MS, level * MAX_AMPLITUDE / MAX)
            else -> HapticPulse(MIN_DURATION_MS + (level - 1) * DURATION_STEP_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}

class Haptics(
    private val vibrator: Vibrator,
    private val prefs: SharedPreferences,
    private val contentResolver: ContentResolver,
) {
    fun strength(channel: HapticChannel) = prefs.getInt(channel.prefKey, HapticStrength.DEFAULT)

    @Suppress("DEPRECATION")
    val isDisabledBySystem: Boolean
        get() = Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0

    fun perform(channel: HapticChannel) {
        if (isDisabledBySystem || !vibrator.hasVibrator()) return
        val pulse = HapticStrength.pulse(strength(channel), vibrator.hasAmplitudeControl()) ?: return
        val effect = VibrationEffect.createOneShot(pulse.durationMs, pulse.amplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
    }

    companion object {
        fun of(context: Context) = Haptics(
            vibrator = defaultVibrator(context),
            prefs = context.inkPrefs(),
            contentResolver = context.contentResolver,
        )

        private fun defaultVibrator(context: Context): Vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
    }
}
