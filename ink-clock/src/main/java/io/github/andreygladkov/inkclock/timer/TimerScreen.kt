package io.github.andreygladkov.inkclock.timer

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.TabScreen
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.ui.DigitEntry
import io.github.andreygladkov.inkclock.ui.DigitKeypad
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class TimerScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val labels = TimerLabels(activity)
    private val store = TimerStore.of(activity)
    private val gap = dp(GAP_DP)
    private var entry = DigitEntry("", ENTRY_DIGITS)

    private val entryView = big()
    private val recent = LinearLayout(activity).apply { orientation = VERTICAL }
    private val setup = LinearLayout(activity).apply {
        orientation = VERTICAL
        addView(entryView, wide())
        addView(View(activity), LayoutParams(0, 0, 1f))
        addView(recent, wide())
        addView(DigitKeypad(activity, onKey = { entry = entry.type(it); showEntry() }, onErase = { entry = entry.erase(); showEntry() }),
            wide().apply { topMargin = gap })
        addView(InkButton(activity).apply {
            setText(R.string.start)
            setOnClickListener { start(entryMs()) }
        }, wide().apply { topMargin = gap })
    }

    private val remaining = big()
    private val until = InkTextView(activity).apply { gravity = Gravity.CENTER_HORIZONTAL }
    private val pauseResume = InkButton(activity)
    private val running = LinearLayout(activity).apply {
        orientation = VERTICAL
        addView(View(activity), LayoutParams(0, 0, 1f))
        addView(remaining, wide())
        addView(until, wide().apply { topMargin = gap })
        addView(View(activity), LayoutParams(0, 0, 1f))
        addView(InkButtonRow(activity, gap, 1f).apply {
            addButton(pauseResume)
            addButton(InkButton(activity).apply {
                setText(R.string.add_minute)
                setOnClickListener { TimerActions.addMinute(activity); show() }
            })
            addButton(InkButton(activity).apply {
                setText(R.string.reset)
                setOnClickListener { TimerActions.reset(activity); show() }
            })
        }, wide())
    }
    private val tick = Runnable { show() }

    init {
        orientation = VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, 0, padding, padding)
        addView(setup, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(running, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        showEntry()
    }

    override fun onResume() = show()

    override fun onPause() {
        removeCallbacks(tick)
    }

    private fun show() {
        removeCallbacks(tick)
        val timer = store.timer()
        setup.visibility = if (timer == Timer.Idle) VISIBLE else GONE
        running.visibility = if (timer == Timer.Idle) GONE else VISIBLE
        when (timer) {
            Timer.Idle -> showRecent()
            is Timer.Running -> {
                val left = timer.remaining(now())
                remaining.text = labels.remaining(left)
                until.text = withLabel(timer.label, activity.getString(R.string.timer_until,
                    AlarmLabels(activity).time(Instant.ofEpochMilli(timer.endsAt).atZone(ZoneId.systemDefault()))))
                pauseResume.setText(R.string.pause)
                pauseResume.setOnClickListener { TimerActions.pause(activity); show() }
                postDelayed(tick, TimerLabels.untilChange(left))
            }
            is Timer.Paused -> {
                remaining.text = labels.remaining(timer.remainingMs)
                until.text = withLabel(timer.label, activity.getString(R.string.timer_on_pause))
                pauseResume.setText(R.string.resume)
                pauseResume.setOnClickListener { TimerActions.resume(activity); show() }
            }
        }
    }

    private fun withLabel(label: String, status: String) = listOf(label, status).filter { it.isNotBlank() }.joinToString(" · ")

    private fun showEntry() {
        val (hours, minutes, seconds) = entry.groups()
        entryView.text = activity.getString(R.string.timer_entry, hours, minutes, seconds)
    }

    private fun showRecent() {
        recent.removeAllViews()
        val durations = store.recent()
        if (durations.isEmpty()) return
        recent.addView(InkButtonRow(activity, gap, InkStyle.SECONDARY_SCALE, isDivided = true).apply {
            durations.forEach { duration ->
                addButton(InkButton(activity).apply {
                    text = labels.duration(duration)
                    isBordered = false
                    setPadding(0, paddingTop, 0, paddingBottom)
                    setOnClickListener { start(duration) }
                })
            }
        }, wide().apply { topMargin = gap })
    }

    private fun start(durationMs: Long) {
        if (durationMs <= 0) return
        TimerActions.start(activity, durationMs)
        entry = DigitEntry("", ENTRY_DIGITS)
        showEntry()
        show()
    }

    private fun entryMs(): Long {
        val (hours, minutes, seconds) = entry.groups()
        return ((hours * 60L + minutes) * 60 + seconds) * 1000
    }

    private fun big() = InkTextView(activity).apply {
        textScale = BIG_SCALE
        isBold = true
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 1
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private fun now() = Instant.now().toEpochMilli()

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val ENTRY_DIGITS = 6
        const val BIG_SCALE = 2.5f
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
    }
}
