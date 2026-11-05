package io.github.andreygladkov.inkclock.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.pomodoro.PomodoroPlan
import io.github.andreygladkov.inkclock.pomodoro.PomodoroStore
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        body.addSection(getString(R.string.settings_alarms))
        body.addView(minutesRow(R.string.settings_snooze, ClockPrefs.SNOOZE_CHOICES, ClockPrefs.snoozeMinutes(this)) { ClockPrefs.setSnoozeMinutes(this, it) })
        body.addView(minutesRow(R.string.settings_ring_for, ClockPrefs.RING_CHOICES, ClockPrefs.ringMinutes(this)) { ClockPrefs.setRingMinutes(this, it) })
        addPomodoro(body)
        body.addSection(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels)
            .forEach { body.addView(it(this)) }
        InkSettings.addHaptics(this, body, listOf(
            HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons),
            HapticChannel.KEYS to getString(R.string.settings_haptic_keys),
        ))
        InkSettings.addA9(this, body)
        body.addSection(getString(R.string.about))
        body.addView(InkButton(this).apply {
            setText(R.string.about)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(AboutActivity.intent(this@SettingsActivity)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(GAP_DP) })
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@SettingsActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    private fun minutesRow(title: Int, choices: List<Int>, current: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), choices.map { it to getString(R.string.minutes_short, it) }, current, onChoose = onChoose)

    private fun addPomodoro(body: LinearLayout) {
        val store = PomodoroStore.of(this)
        val plan = store.plan()
        body.addSection(getString(R.string.settings_pomodoro))
        body.addView(minutesRow(R.string.settings_pomodoro_work, PomodoroPlan.WORK_CHOICES, plan.workMinutes) { store.savePlan(store.plan().copy(workMinutes = it)) })
        body.addView(minutesRow(R.string.settings_pomodoro_short, PomodoroPlan.SHORT_BREAK_CHOICES, plan.shortBreakMinutes) { store.savePlan(store.plan().copy(shortBreakMinutes = it)) })
        body.addView(minutesRow(R.string.settings_pomodoro_long, PomodoroPlan.LONG_BREAK_CHOICES, plan.longBreakMinutes) { store.savePlan(store.plan().copy(longBreakMinutes = it)) })
        body.addView(countRow(R.string.settings_pomodoro_cycle, PomodoroPlan.CYCLE_CHOICES, plan.cycleLength) { store.savePlan(store.plan().copy(cycleLength = it)) })
        body.addView(countRow(R.string.settings_pomodoro_goal, PomodoroPlan.GOAL_CHOICES, plan.dailyGoal) { store.savePlan(store.plan().copy(dailyGoal = it)) })
        body.addView(InkSettings.onOff(this, R.string.settings_pomodoro_screen, store.keepsScreenOn) { store.keepsScreenOn = it })
        body.addView(InkSettings.onOff(this, R.string.settings_pomodoro_sound, store.playsSound) { store.playsSound = it })
    }

    private fun countRow(title: Int, choices: List<Int>, current: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), choices.map { it to it.toString() }, current, onChoose = onChoose)

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@SettingsActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        addView(InkTextView(this@SettingsActivity).apply {
            setText(R.string.settings)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
