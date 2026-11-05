package io.github.andreygladkov.inkclock.timer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkclock.ClockPrefs

class TimerStore(private val prefs: SharedPreferences) {
    fun timer(): Timer {
        val duration = prefs.getLong(KEY_DURATION, 0)
        val label = prefs.getString(KEY_LABEL, "").orEmpty()
        return when {
            prefs.contains(KEY_ENDS_AT) -> Timer.Running(duration, prefs.getLong(KEY_ENDS_AT, 0), label)
            prefs.contains(KEY_REMAINING) -> Timer.Paused(duration, prefs.getLong(KEY_REMAINING, 0), label)
            else -> Timer.Idle
        }
    }

    fun save(timer: Timer) = prefs.edit(commit = true) {
        remove(KEY_ENDS_AT)
        remove(KEY_REMAINING)
        when (timer) {
            Timer.Idle -> {
                remove(KEY_DURATION)
                remove(KEY_LABEL)
            }
            is Timer.Running -> {
                putLong(KEY_DURATION, timer.durationMs)
                putLong(KEY_ENDS_AT, timer.endsAt)
                putString(KEY_LABEL, timer.label)
            }
            is Timer.Paused -> {
                putLong(KEY_DURATION, timer.durationMs)
                putLong(KEY_REMAINING, timer.remainingMs)
                putString(KEY_LABEL, timer.label)
            }
        }
    }

    fun recent(): List<Long> = prefs.getString(KEY_RECENT, "").orEmpty().split(',').mapNotNull(String::toLongOrNull)

    fun remember(durationMs: Long) = prefs.edit(commit = true) {
        putString(KEY_RECENT, (listOf(durationMs) + recent().filter { it != durationMs }).take(RECENT_COUNT).joinToString(","))
    }

    companion object {
        const val RECENT_COUNT = 4
        private const val PREFS = "timer"
        private const val KEY_DURATION = "duration"
        private const val KEY_ENDS_AT = "ends_at"
        private const val KEY_REMAINING = "remaining"
        private const val KEY_LABEL = "label"
        private const val KEY_RECENT = "recent"

        fun of(context: Context) = TimerStore(ClockPrefs.deviceStorage(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
