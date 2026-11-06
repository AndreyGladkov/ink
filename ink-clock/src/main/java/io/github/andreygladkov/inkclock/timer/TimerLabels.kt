package io.github.andreygladkov.inkclock.timer

import android.content.Context
import io.github.andreygladkov.inkclock.R

/** Above a minute only whole minutes are shown, so the e-ink panel redraws once a minute rather than every second. */
class TimerLabels(private val context: Context) {
    fun remaining(ms: Long): String {
        val seconds = ceilDiv(ms, SECOND_MS)
        if (seconds < 60) return context.getString(R.string.duration_seconds, seconds)
        return minutes(ceilDiv(ms, MINUTE_MS))
    }

    fun duration(ms: Long): String {
        val seconds = ms / SECOND_MS
        val parts = listOfNotNull(
            (seconds / 3600).takeIf { it > 0 }?.let { context.getString(R.string.duration_hours, it) },
            (seconds / 60 % 60).takeIf { it > 0 }?.let { context.getString(R.string.duration_minutes, it) },
            (seconds % 60).takeIf { it > 0 }?.let { context.getString(R.string.duration_seconds, it) },
        )
        return parts.joinToString(" ").ifEmpty { context.getString(R.string.duration_seconds, 0L) }
    }

    private fun minutes(total: Long): String {
        val hours = total / 60
        val minutes = total % 60
        return when {
            hours == 0L -> context.getString(R.string.duration_minutes, minutes)
            minutes == 0L -> context.getString(R.string.duration_hours, hours)
            else -> context.getString(R.string.duration_hours_minutes, hours, minutes)
        }
    }

    companion object {
        const val SECOND_MS = 1000L
        const val MINUTE_MS = 60_000L

        /** How long the shown remaining time stays the same, so the screen redraws exactly when it changes. */
        fun untilChange(remainingMs: Long): Long = when {
            remainingMs <= 0 -> SECOND_MS
            remainingMs <= MINUTE_MS -> (remainingMs - 1) % SECOND_MS + 1
            else -> (remainingMs - 1) % MINUTE_MS + 1
        }

        private fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b
    }
}
