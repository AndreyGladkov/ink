package io.github.andreygladkov.inkclock.stopwatch

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkclock.ClockPrefs

class StopwatchStore(private val prefs: SharedPreferences) {
    fun stopwatch() = Stopwatch(
        elapsedMs = prefs.getLong(KEY_ELAPSED, 0),
        startedAt = if (prefs.contains(KEY_STARTED_AT)) prefs.getLong(KEY_STARTED_AT, 0) else null,
        laps = prefs.getString(KEY_LAPS, "").orEmpty().split(',').mapNotNull(String::toLongOrNull),
    )

    fun save(stopwatch: Stopwatch) = prefs.edit(commit = true) {
        putLong(KEY_ELAPSED, stopwatch.elapsedMs)
        stopwatch.startedAt?.let { putLong(KEY_STARTED_AT, it) } ?: remove(KEY_STARTED_AT)
        putString(KEY_LAPS, stopwatch.laps.joinToString(","))
    }

    companion object {
        private const val PREFS = "stopwatch"
        private const val KEY_ELAPSED = "elapsed"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_LAPS = "laps"

        fun of(context: Context) = StopwatchStore(ClockPrefs.deviceStorage(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
