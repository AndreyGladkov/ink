package io.github.andreygladkov.inkclock.pomodoro

enum class Phase { WORK, SHORT_BREAK, LONG_BREAK }

data class PomodoroPlan(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val cycleLength: Int = 4,
    val dailyGoal: Int = 8,
) {
    fun minutes(phase: Phase) = when (phase) {
        Phase.WORK -> workMinutes
        Phase.SHORT_BREAK -> shortBreakMinutes
        Phase.LONG_BREAK -> longBreakMinutes
    }

    companion object {
        val WORK_CHOICES = listOf(20, 25, 30, 45, 50)
        val SHORT_BREAK_CHOICES = listOf(3, 5, 10)
        val LONG_BREAK_CHOICES = listOf(10, 15, 20, 30)
        val CYCLE_CHOICES = listOf(2, 3, 4, 5)
        val GOAL_CHOICES = listOf(4, 6, 8, 10)
    }
}

/**
 * [phase] is the phase under way, or the one waiting to be started when [startedAt] is null: a phase that ends
 * never starts the next by itself. [doneToday] counts finished work phases on the epoch day [day].
 */
data class Pomodoro(
    val phase: Phase = Phase.WORK,
    val startedAt: Long? = null,
    val cycleDone: Int = 0,
    val doneToday: Int = 0,
    val day: Long = 0,
) {
    val isRunning get() = startedAt != null

    fun start(now: Long) = if (isRunning) this else copy(startedAt = now)

    fun stop() = copy(startedAt = null)

    fun endsAt(plan: PomodoroPlan): Long? = startedAt?.plus(plan.minutes(phase) * MINUTE_MS)

    fun elapsedMinutes(now: Long): Int = startedAt?.let { ((now - it) / MINUTE_MS).toInt() } ?: 0

    fun doneOn(today: Long) = if (day == today) doneToday else 0

    fun finish(plan: PomodoroPlan, today: Long): Pomodoro = when (phase) {
        Phase.WORK -> {
            val cycle = cycleDone + 1
            copy(
                phase = if (cycle >= plan.cycleLength) Phase.LONG_BREAK else Phase.SHORT_BREAK,
                startedAt = null,
                cycleDone = cycle,
                doneToday = doneOn(today) + 1,
                day = today,
            )
        }
        Phase.SHORT_BREAK, Phase.LONG_BREAK -> skipBreak()
    }

    fun skipBreak() = if (phase == Phase.WORK) this else copy(phase = Phase.WORK, startedAt = null, cycleDone = if (phase == Phase.LONG_BREAK) 0 else cycleDone)

    companion object {
        const val MINUTE_MS = 60_000L
    }
}
