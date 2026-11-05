package io.github.andreygladkov.inkclock.pomodoro

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkclock.ClockPrefs

class PomodoroStore(private val prefs: SharedPreferences) {
    fun pomodoro() = Pomodoro(
        phase = prefs.getString(KEY_PHASE, null)?.let { name -> Phase.entries.firstOrNull { it.name == name } } ?: Phase.WORK,
        startedAt = if (prefs.contains(KEY_STARTED_AT)) prefs.getLong(KEY_STARTED_AT, 0) else null,
        cycleDone = prefs.getInt(KEY_CYCLE_DONE, 0),
        doneToday = prefs.getInt(KEY_DONE_TODAY, 0),
        day = prefs.getLong(KEY_DAY, 0),
    )

    fun save(pomodoro: Pomodoro) = prefs.edit(commit = true) {
        putString(KEY_PHASE, pomodoro.phase.name)
        pomodoro.startedAt?.let { putLong(KEY_STARTED_AT, it) } ?: remove(KEY_STARTED_AT)
        putInt(KEY_CYCLE_DONE, pomodoro.cycleDone)
        putInt(KEY_DONE_TODAY, pomodoro.doneToday)
        putLong(KEY_DAY, pomodoro.day)
    }

    fun plan() = PomodoroPlan(
        workMinutes = prefs.getInt(KEY_WORK, DEFAULT.workMinutes),
        shortBreakMinutes = prefs.getInt(KEY_SHORT_BREAK, DEFAULT.shortBreakMinutes),
        longBreakMinutes = prefs.getInt(KEY_LONG_BREAK, DEFAULT.longBreakMinutes),
        cycleLength = prefs.getInt(KEY_CYCLE, DEFAULT.cycleLength),
        dailyGoal = prefs.getInt(KEY_GOAL, DEFAULT.dailyGoal),
    )

    fun savePlan(plan: PomodoroPlan) = prefs.edit {
        putInt(KEY_WORK, plan.workMinutes)
        putInt(KEY_SHORT_BREAK, plan.shortBreakMinutes)
        putInt(KEY_LONG_BREAK, plan.longBreakMinutes)
        putInt(KEY_CYCLE, plan.cycleLength)
        putInt(KEY_GOAL, plan.dailyGoal)
    }

    var keepsScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }

    var playsSound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit { putBoolean(KEY_SOUND, value) }

    companion object {
        private val DEFAULT = PomodoroPlan()
        private const val PREFS = "pomodoro"
        private const val KEY_PHASE = "phase"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_CYCLE_DONE = "cycle_done"
        private const val KEY_DONE_TODAY = "done_today"
        private const val KEY_DAY = "day"
        private const val KEY_WORK = "work_minutes"
        private const val KEY_SHORT_BREAK = "short_break_minutes"
        private const val KEY_LONG_BREAK = "long_break_minutes"
        private const val KEY_CYCLE = "cycle_length"
        private const val KEY_GOAL = "daily_goal"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SOUND = "sound"

        fun of(context: Context) = PomodoroStore(ClockPrefs.deviceStorage(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}
