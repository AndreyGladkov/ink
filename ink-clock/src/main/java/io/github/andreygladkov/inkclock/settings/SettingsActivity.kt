package io.github.andreygladkov.inkclock.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.desk.ClockDreamSetting
import io.github.andreygladkov.inkclock.pomodoro.PomodoroPlan
import io.github.andreygladkov.inkclock.pomodoro.PomodoroStore
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    override fun headerTitle(): String = getString(R.string.settings)

    override fun outsideState() = ClockDreamSetting.isSelected(this)

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        section(getString(R.string.settings_alarms))
        item { minutesRow(R.string.settings_snooze, ClockPrefs.SNOOZE_CHOICES, ClockPrefs.snoozeMinutes(activity)) { ClockPrefs.setSnoozeMinutes(activity, it) } }
        item { minutesRow(R.string.settings_ring_for, ClockPrefs.RING_CHOICES, ClockPrefs.ringMinutes(activity)) { ClockPrefs.setRingMinutes(activity, it) } }
        addPomodoro()
        section(getString(R.string.settings_dream))
        item { InkSettings.hint(activity, getString(R.string.dream_hint)) }
        item { InkSettings.hint(activity, getString(if (ClockDreamSetting.isSelected(activity)) R.string.dream_selected else R.string.dream_not_selected)) }
        item { InkSettings.rowButton(activity, getString(R.string.dream_choose)) { startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) } }
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels)
            .forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(
            HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons),
            HapticChannel.KEYS to getString(R.string.settings_haptic_keys),
        ))
        InkSettings.addA9(activity, this)
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    private fun minutesRow(title: Int, choices: List<Int>, current: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), choices.map { it to getString(R.string.minutes_short, it) }, current, onChoose = onChoose)

    private fun InkPagedItems.addPomodoro() {
        val store = PomodoroStore.of(this@SettingsActivity)
        section(getString(R.string.settings_pomodoro))
        item { minutesRow(R.string.settings_pomodoro_work, PomodoroPlan.WORK_CHOICES, store.plan().workMinutes) { store.savePlan(store.plan().copy(workMinutes = it)) } }
        item { minutesRow(R.string.settings_pomodoro_short, PomodoroPlan.SHORT_BREAK_CHOICES, store.plan().shortBreakMinutes) { store.savePlan(store.plan().copy(shortBreakMinutes = it)) } }
        item { minutesRow(R.string.settings_pomodoro_long, PomodoroPlan.LONG_BREAK_CHOICES, store.plan().longBreakMinutes) { store.savePlan(store.plan().copy(longBreakMinutes = it)) } }
        item { countRow(R.string.settings_pomodoro_cycle, PomodoroPlan.CYCLE_CHOICES, store.plan().cycleLength) { store.savePlan(store.plan().copy(cycleLength = it)) } }
        item { countRow(R.string.settings_pomodoro_goal, PomodoroPlan.GOAL_CHOICES, store.plan().dailyGoal) { store.savePlan(store.plan().copy(dailyGoal = it)) } }
        item { InkSettings.onOff(this@SettingsActivity, R.string.settings_pomodoro_screen, store.keepsScreenOn) { store.keepsScreenOn = it } }
        item { InkSettings.onOff(this@SettingsActivity, R.string.settings_pomodoro_sound, store.playsSound) { store.playsSound = it } }
    }

    private fun countRow(title: Int, choices: List<Int>, current: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), choices.map { it to it.toString() }, current, onChoose = onChoose)

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
