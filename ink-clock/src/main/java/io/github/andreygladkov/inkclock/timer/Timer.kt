package io.github.andreygladkov.inkclock.timer

sealed interface Timer {
    data object Idle : Timer

    data class Running(val durationMs: Long, val endsAt: Long, val label: String = "") : Timer {
        fun remaining(now: Long) = (endsAt - now).coerceAtLeast(0)
        fun pause(now: Long) = Paused(durationMs, remaining(now), label)
        fun plus(ms: Long) = copy(durationMs = durationMs + ms, endsAt = endsAt + ms)
    }

    data class Paused(val durationMs: Long, val remainingMs: Long, val label: String = "") : Timer {
        fun resume(now: Long) = Running(durationMs, now + remainingMs, label)
        fun plus(ms: Long) = copy(durationMs = durationMs + ms, remainingMs = remainingMs + ms)
    }

    companion object {
        fun start(durationMs: Long, now: Long, label: String = "") = Running(durationMs, now + durationMs, label)
    }
}
