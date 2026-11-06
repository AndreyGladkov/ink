package io.github.andreygladkov.inkclock.stopwatch

import android.content.Context
import io.github.andreygladkov.inkclock.notify.ClockNotifications
import java.time.Instant

object StopwatchActions {
    fun start(context: Context, now: Long = now()) = update(context) { it.start(now) }

    fun pause(context: Context, now: Long = now()) = update(context) { it.pause(now) }

    fun lap(context: Context, now: Long = now()) = update(context) { it.lap(now) }

    fun reset(context: Context) = update(context) { Stopwatch() }

    private fun update(context: Context, change: (Stopwatch) -> Stopwatch): Stopwatch {
        val store = StopwatchStore.of(context)
        val changed = change(store.stopwatch())
        store.save(changed)
        ClockNotifications.showStopwatch(context, changed)
        return changed
    }

    private fun now() = Instant.now().toEpochMilli()
}
