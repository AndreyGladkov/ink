package io.github.andreygladkov.inkclock.pomodoro

/**
 * How much of the tomato is black, in whole minutes: work eats it clockwise from the stem and a break fills it
 * back the same way, so a phase ends with the picture the next one starts from and the panel never jumps.
 */
data class PomodoroDial(val minutes: Int, val black: Int, val refills: Boolean) {
    val sweepDegrees get() = FULL_TURN * black / minutes
    val startDegrees get() = if (refills) TOP else TOP + FULL_TURN * (minutes - black) / minutes

    companion object {
        private const val FULL_TURN = 360f
        private const val TOP = -90f

        fun of(pomodoro: Pomodoro, plan: PomodoroPlan, now: Long): PomodoroDial {
            val minutes = plan.minutes(pomodoro.phase)
            val elapsed = pomodoro.elapsedMinutes(now).coerceIn(0, minutes)
            return when (pomodoro.phase) {
                Phase.WORK -> PomodoroDial(minutes, minutes - elapsed, refills = false)
                Phase.SHORT_BREAK, Phase.LONG_BREAK -> PomodoroDial(minutes, elapsed, refills = true)
            }
        }
    }
}
