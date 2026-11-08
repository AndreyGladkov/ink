package io.github.andreygladkov.inklauncher.screentime

import android.content.Context
import io.github.andreygladkov.inklauncher.R

object ScreenTimeText {
    fun duration(context: Context, millis: Long): String = format(context, millis, R.string.duration_hours_minutes)

    fun shortDuration(context: Context, millis: Long): String = format(context, millis, R.string.duration_short)

    private fun format(context: Context, millis: Long, withHours: Int): String {
        val minutes = millis / MINUTE_MS
        val hours = minutes / 60
        return if (hours > 0) context.getString(withHours, hours, minutes % 60) else context.getString(R.string.duration_minutes, minutes)
    }

    fun unlocks(context: Context, count: Int): String = context.resources.getQuantityString(R.plurals.unlocks, count, count)

    const val MINUTE_MS = 60_000L
}
