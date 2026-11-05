package io.github.andreygladkov.inkclock.ring

import io.github.andreygladkov.inkclock.alarm.Alarm
import io.github.andreygladkov.inkclock.alarm.Ringtone

sealed interface Ring {
    val ringtone: Ringtone
    val vibrates: Boolean

    data class OfAlarm(val alarm: Alarm) : Ring {
        override val ringtone get() = alarm.ringtone
        override val vibrates get() = alarm.vibrates
    }

    data class OfTimer(val durationMs: Long, val label: String) : Ring {
        override val ringtone get() = Ringtone.Default
        override val vibrates get() = true
    }
}
