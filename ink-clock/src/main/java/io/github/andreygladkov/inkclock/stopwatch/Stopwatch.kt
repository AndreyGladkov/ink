package io.github.andreygladkov.inkclock.stopwatch

/** Laps are kept as the total time at which each was marked; a lap's own length is the difference to the one before. */
data class Stopwatch(val elapsedMs: Long = 0, val startedAt: Long? = null, val laps: List<Long> = emptyList()) {
    val isRunning get() = startedAt != null
    val isIdle get() = !isRunning && elapsedMs == 0L

    fun elapsed(now: Long) = elapsedMs + (startedAt?.let { now - it } ?: 0)

    fun start(now: Long) = if (isRunning) this else copy(startedAt = now)

    fun pause(now: Long) = Stopwatch(elapsed(now), null, laps)

    fun lap(now: Long) = if (isRunning) copy(laps = laps + elapsed(now)) else this

    fun lapLengths(): List<Long> = laps.mapIndexed { index, total -> total - (laps.getOrNull(index - 1) ?: 0) }
}
