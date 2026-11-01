package io.github.andreygladkov.inkphone.haptics

import android.os.VibrationEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HapticStrengthTest {
    @Test fun offProducesNoPulse() {
        assertNull(HapticStrength.pulse(HapticStrength.OFF, hasAmplitudeControl = true))
        assertNull(HapticStrength.pulse(HapticStrength.OFF, hasAmplitudeControl = false))
    }

    @Test fun amplitudeControlScalesAmplitudeAtFixedDuration() {
        val pulses = (1..HapticStrength.MAX).map { HapticStrength.pulse(it, hasAmplitudeControl = true)!! }
        assertEquals(1, pulses.map { it.durationMs }.distinct().size)
        assertEquals(listOf(51, 102, 153, 204, 255), pulses.map { it.amplitude })
    }

    @Test fun withoutAmplitudeControlScalesDuration() {
        val pulses = (1..HapticStrength.MAX).map { HapticStrength.pulse(it, hasAmplitudeControl = false)!! }
        assertEquals(listOf(16L, 24L, 32L, 40L, 48L), pulses.map { it.durationMs })
        assertEquals(setOf(VibrationEffect.DEFAULT_AMPLITUDE), pulses.map { it.amplitude }.toSet())
    }

    @Test fun strengthAboveMaxIsClamped() {
        assertEquals(HapticStrength.pulse(HapticStrength.MAX, true), HapticStrength.pulse(HapticStrength.MAX + 3, true))
    }
}
