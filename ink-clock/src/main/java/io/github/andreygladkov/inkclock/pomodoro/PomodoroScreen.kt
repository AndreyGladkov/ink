package io.github.andreygladkov.inkclock.pomodoro

import android.app.Activity
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeIcons
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.TabScreen
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class PomodoroScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val store = PomodoroStore.of(activity)
    private val style = InkStyle.of(activity)
    private val padding = dp(PADDING_DP)
    private val title = InkTextView(activity).apply {
        textScale = InkStyle.DISPLAY_SCALE
        isBold = true
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val status = InkTextView(activity).apply { gravity = Gravity.CENTER_HORIZONTAL }
    private val tomato = TomatoView(activity)
    private val cycle = InkTextView(activity).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        textScale = InkStyle.DISPLAY_SCALE
    }
    private val main = InkButton(activity)
    private val skip = InkButton(activity).apply {
        setText(R.string.pomodoro_skip_break)
        setOnClickListener { PomodoroActions.skipBreak(activity); show() }
    }
    private val today = InkTextView(activity).apply { textScale = InkStyle.SECONDARY_SCALE }
    private val tomatoes = LinearLayout(activity).apply { orientation = HORIZONTAL }
    private val tick = Runnable { show() }

    init {
        orientation = VERTICAL
        setPadding(padding, 0, padding, padding)
        addView(title, wide())
        addView(status, wide())
        addView(tomato, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = padding; bottomMargin = padding })
        addView(cycle, wide())
        addView(InkButtonRow(activity, padding / 2, 1f).apply {
            addButton(main)
            addButton(skip)
        }, wide().apply { topMargin = padding / 2 })
        addView(today, wide().apply { topMargin = padding })
        addView(tomatoes, wide().apply { topMargin = padding / 2 })
    }

    override fun onResume() = show()

    override fun onPause() {
        removeCallbacks(tick)
        keepScreenOn(false)
    }

    private fun show() {
        removeCallbacks(tick)
        val now = Instant.now().toEpochMilli()
        if (PomodoroActions.finishIfDue(activity, now)) return show()
        val pomodoro = store.pomodoro()
        val plan = store.plan()
        title.text = PomodoroLabels.phase(activity, pomodoro.phase)
        tomato.dial = PomodoroDial.of(pomodoro, plan, now)
        cycle.text = cycleDots(pomodoro, plan)
        skip.visibility = if (pomodoro.phase == Phase.WORK) GONE else VISIBLE
        val endsAt = pomodoro.endsAt(plan)
        if (endsAt == null) {
            status.text = activity.getString(R.string.duration_minutes, plan.minutes(pomodoro.phase).toLong())
            main.text = PomodoroLabels.startButton(activity, pomodoro.phase)
            main.setOnClickListener { PomodoroActions.start(activity); show() }
        } else {
            val left = (endsAt - now + MINUTE_MS - 1) / MINUTE_MS
            status.text = activity.getString(R.string.pomodoro_left, left,
                AlarmLabels(activity).time(Instant.ofEpochMilli(endsAt).atZone(ZoneId.systemDefault())))
            main.setText(R.string.stop)
            main.setOnClickListener { PomodoroActions.stop(activity); show() }
            postDelayed(tick, MINUTE_MS - (now - pomodoro.startedAt!!) % MINUTE_MS)
        }
        showToday(pomodoro.doneOn(PomodoroActions.today(now)), plan.dailyGoal)
        keepScreenOn(pomodoro.isRunning && store.keepsScreenOn)
    }

    private fun cycleDots(pomodoro: Pomodoro, plan: PomodoroPlan): String {
        val done = pomodoro.cycleDone.coerceAtMost(plan.cycleLength)
        return (0 until plan.cycleLength).joinToString(" ") { if (it < done) FILLED_DOT else EMPTY_DOT }
    }

    private fun showToday(done: Int, goal: Int) {
        today.text = activity.getString(R.string.pomodoro_today, done, goal)
        tomatoes.removeAllViews()
        val size = dp(TOMATO_DP)
        for (index in 0 until maxOf(goal, done).coerceAtMost(MAX_TOMATOES)) {
            val icon = if (index < done) R.drawable.ic_tomato_done else R.drawable.ic_tab_pomodoro
            tomatoes.addView(ImageView(activity).apply { setImageBitmap(MonochromeIcons.bitmap(activity, icon, size, style.foreground)) },
                LayoutParams(size, size).apply { if (index > 0) marginStart = dp(TOMATO_GAP_DP) })
        }
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 16f
        const val TOMATO_DP = 24f
        const val TOMATO_GAP_DP = 4f
        const val MAX_TOMATOES = 11
        const val MINUTE_MS = 60_000L
        const val FILLED_DOT = "●"
        const val EMPTY_DOT = "○"
    }
}
