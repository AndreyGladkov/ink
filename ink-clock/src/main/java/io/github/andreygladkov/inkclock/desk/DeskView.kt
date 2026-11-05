package io.github.andreygladkov.inkclock.desk

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.alarm.AlarmScheduler
import io.github.andreygladkov.inkclock.alarm.AlarmStore
import io.github.andreygladkov.inkclock.pomodoro.PomodoroActions
import io.github.andreygladkov.inkclock.pomodoro.PomodoroDial
import io.github.andreygladkov.inkclock.pomodoro.PomodoroLabels
import io.github.andreygladkov.inkclock.pomodoro.PomodoroStore
import io.github.andreygladkov.inkclock.pomodoro.TomatoView
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The desk clock and the screensaver: the time, or the pomodoro under way. It redraws once a minute and has
 * the A9 panel fully refreshed on the hour, so ghosts of earlier minutes do not build up overnight.
 */
class DeskView(context: Context) : LinearLayout(context) {
    private val locale = resources.configuration.locales[0]
    private val alarmLabels = AlarmLabels(context)
    private val dateFormat = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    private val padding = dp(PADDING_DP)

    private val time = text(TIME_SCALE, bold = true)
    private val date = text(InkStyle.TITLE_SCALE)
    private val alarm = text(InkStyle.TITLE_SCALE)
    private val clock = column(time, date, alarm)

    private val phase = text(InkStyle.DISPLAY_SCALE, bold = true)
    private val status = text(InkStyle.TITLE_SCALE)
    private val tomato = TomatoView(context)
    private val pomodoro = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(phase, wide())
        addView(status, wide())
        addView(tomato, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = padding })
    }

    private var lastShownHour = -1
    private val tick = Runnable { show() }

    init {
        orientation = VERTICAL
        setBackgroundColor(InkStyle.of(context).background)
        setPadding(padding, padding, padding, padding)
        addView(clock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(pomodoro, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun start() = show()

    fun stop() {
        removeCallbacks(tick)
        lastShownHour = -1
    }

    private fun show() {
        removeCallbacks(tick)
        val now = ZonedDateTime.now()
        val nowMs = now.toInstant().toEpochMilli()
        PomodoroActions.finishIfDue(context, nowMs)
        val store = PomodoroStore.of(context)
        val running = store.pomodoro().takeIf { it.isRunning }
        clock.visibility = if (running == null) VISIBLE else GONE
        pomodoro.visibility = if (running == null) GONE else VISIBLE
        if (running == null) showClock(now) else {
            val plan = store.plan()
            val endsAt = running.endsAt(plan)!!
            phase.text = PomodoroLabels.phase(context, running.phase)
            status.text = context.getString(R.string.pomodoro_left, (endsAt - nowMs + MINUTE_MS - 1) / MINUTE_MS,
                alarmLabels.time(Instant.ofEpochMilli(endsAt).atZone(ZoneId.systemDefault())))
            tomato.dial = PomodoroDial.of(running, plan, nowMs)
        }
        if (lastShownHour != -1 && now.hour != lastShownHour) A9Panel.clearSoon(context)
        lastShownHour = now.hour
        postDelayed(tick, untilNextMinute(nowMs, running?.startedAt))
    }

    private fun showClock(now: ZonedDateTime) {
        time.text = alarmLabels.time(now)
        date.text = now.format(dateFormat)
        val next = AlarmScheduler.next(AlarmStore.of(context).all(), now)
        alarm.text = next?.let { context.getString(R.string.desk_next_alarm, alarmLabels.moment(it.at, now)) }.orEmpty()
        alarm.visibility = if (next == null) GONE else VISIBLE
    }

    private fun column(vararg views: View) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        views.forEach { addView(it, wide()) }
    }

    private fun text(scale: Float, bold: Boolean = false) = InkTextView(context).apply {
        textScale = scale
        isBold = bold
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun wide() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 24f
        private const val TIME_SCALE = 6f
        private const val MINUTE_MS = 60_000L

        /** The next wall-clock minute, or the next minute of a running pomodoro if that comes first. */
        fun untilNextMinute(nowMs: Long, pomodoroStartedAt: Long?): Long {
            val wall = MINUTE_MS - nowMs % MINUTE_MS
            val phase = pomodoroStartedAt?.let { MINUTE_MS - (nowMs - it) % MINUTE_MS } ?: wall
            return minOf(wall, phase)
        }
    }
}
