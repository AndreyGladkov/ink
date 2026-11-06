package io.github.andreygladkov.inkclock.alarm

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.TabScreen
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class AlarmListScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val labels = AlarmLabels(activity)
    private val padding = dp(PADDING_DP)
    private val next = InkTextView(activity).apply {
        textScale = InkStyle.SECONDARY_SCALE
        setPadding(padding, 0, padding, padding)
    }
    private val list = InkPagedList(activity)
    private val empty = InkTextView(activity).apply {
        setText(R.string.no_alarms)
        gravity = Gravity.CENTER
    }
    private var alarms = emptyList<Alarm>()
    private val tick = Runnable { showNext() }

    init {
        orientation = VERTICAL
        addView(next, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(empty)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onResume() = load()

    override fun onPause() {
        removeCallbacks(tick)
    }

    private fun load() {
        alarms = AlarmStore.of(activity).all()
        empty.visibility = if (alarms.isEmpty()) VISIBLE else GONE
        list.visibility = if (alarms.isEmpty()) INVISIBLE else VISIBLE
        list.setAdapter(object : InkPagedList.Adapter {
            override val count get() = alarms.size
            override fun itemId(position: Int) = alarms[position].hashCode().toLong()
            override fun createView(position: Int) = row(alarms[position])
        }, list.firstPosition)
        showNext()
    }

    private fun showNext() {
        removeCallbacks(tick)
        val now = ZonedDateTime.now()
        val upcoming = AlarmScheduler.next(alarms, now)
        next.text = upcoming?.let { labels.next(it, now) } ?: activity.getString(R.string.no_next_alarm)
        if (upcoming != null) postDelayed(tick, MINUTE_MS - now.toInstant().toEpochMilli() % MINUTE_MS)
    }

    private fun row(alarm: Alarm): View = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(padding, dp(InkPagedList.ROW_PADDING_DP), padding, dp(InkPagedList.ROW_PADDING_DP))
        setOnClickListener { activity.startActivity(AlarmEditActivity.edit(activity, alarm.id)) }
        addView(LinearLayout(activity).apply {
            orientation = VERTICAL
            addView(InkTextView(activity).apply {
                text = labels.time(alarm.hour, alarm.minute)
                textScale = TIME_SCALE
                isBold = alarm.isEnabled
            })
            addView(InkTextView(activity).apply {
                text = labels.summary(alarm)
                textScale = InkStyle.SECONDARY_SCALE
                maxLines = 2
            })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(InkButton(activity).apply {
            setText(if (alarm.isEnabled) InkR.string.on else InkR.string.off)
            contentDescription = activity.getString(R.string.toggle_alarm, labels.time(alarm.hour, alarm.minute))
            isSelected = alarm.isEnabled
            minWidth = dp(TOGGLE_WIDTH_DP)
            setOnClickListener {
                AlarmActions.save(activity, alarm.copy(isEnabled = !alarm.isEnabled))
                load()
            }
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 16f
        const val TOGGLE_WIDTH_DP = 88f
        const val TIME_SCALE = 2f
        const val MINUTE_MS = 60_000L
    }
}
