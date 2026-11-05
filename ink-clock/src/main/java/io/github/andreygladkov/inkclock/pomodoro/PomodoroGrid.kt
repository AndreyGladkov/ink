package io.github.andreygladkov.inkclock.pomodoro

/**
 * One cell per minute: work fills cells as minutes pass and a break empties them, so each minute changes one
 * cell and the e-ink panel never animates. Up to 25 cells sit five to a row; longer phases ten to a row.
 */
data class PomodoroGrid(val cells: Int, val solid: IntRange, val current: Int?) {
    val columns get() = if (cells <= NARROW_MAX) NARROW else WIDE
    val rows get() = (cells + columns - 1) / columns

    companion object {
        private const val NARROW_MAX = 25
        private const val NARROW = 5
        private const val WIDE = 10

        fun of(pomodoro: Pomodoro, plan: PomodoroPlan, now: Long): PomodoroGrid {
            val cells = plan.minutes(pomodoro.phase)
            val elapsed = pomodoro.elapsedMinutes(now).coerceIn(0, cells)
            val current = elapsed.takeIf { pomodoro.isRunning && it < cells }
            return when (pomodoro.phase) {
                Phase.WORK -> PomodoroGrid(cells, 0 until elapsed, current)
                Phase.SHORT_BREAK, Phase.LONG_BREAK -> PomodoroGrid(cells, (if (pomodoro.isRunning) elapsed + 1 else 0) until cells, current)
            }
        }
    }
}
