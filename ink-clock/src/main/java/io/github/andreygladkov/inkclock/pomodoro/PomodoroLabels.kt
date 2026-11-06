package io.github.andreygladkov.inkclock.pomodoro

import android.content.Context
import io.github.andreygladkov.inkclock.R

object PomodoroLabels {
    fun phase(context: Context, phase: Phase): String = context.getString(when (phase) {
        Phase.WORK -> R.string.pomodoro_work
        Phase.SHORT_BREAK -> R.string.pomodoro_short_break
        Phase.LONG_BREAK -> R.string.pomodoro_long_break
    })

    fun nextPrompt(context: Context, phase: Phase, plan: PomodoroPlan): String =
        context.getString(if (phase == Phase.WORK) R.string.pomodoro_start_work else R.string.pomodoro_start_break, plan.minutes(phase))

    fun startButton(context: Context, phase: Phase): String =
        context.getString(if (phase == Phase.WORK) R.string.pomodoro_begin_work else R.string.pomodoro_begin_break)
}
