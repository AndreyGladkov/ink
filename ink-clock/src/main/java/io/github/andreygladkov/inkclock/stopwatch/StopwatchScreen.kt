package io.github.andreygladkov.inkclock.stopwatch

import android.app.Activity
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.TabScreen
import java.time.Instant
import kotlin.math.roundToInt

/** Seconds tick only while this tab is on screen; the notification shows a fixed start time instead. */
class StopwatchScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val store = StopwatchStore.of(activity)
    private val padding = dp(PADDING_DP)
    private val elapsed = InkTextView(activity).apply {
        textScale = BIG_SCALE
        isBold = true
        gravity = Gravity.CENTER_HORIZONTAL
        maxLines = 1
    }
    private val secondaryAction = InkButton(activity)
    private val primaryAction = InkButton(activity)
    private val laps = InkPagedList(activity)
    private var shownLaps: List<Long>? = null
    private val tick = Runnable { show() }

    init {
        orientation = VERTICAL
        addView(elapsed, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = padding })
        addView(laps, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = padding })
        addView(InkButtonRow(activity, padding / 2, 1f).apply {
            setPadding(padding, padding / 2, padding, padding)
            addButton(secondaryAction)
            addButton(primaryAction)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    override fun onResume() = show()

    override fun onPause() {
        removeCallbacks(tick)
    }

    private fun show() {
        removeCallbacks(tick)
        val stopwatch = store.stopwatch()
        val now = now()
        val ms = stopwatch.elapsed(now)
        elapsed.text = StopwatchLabels.elapsed(ms, tenths = !stopwatch.isRunning)
        (primaryAction.layoutParams as MarginLayoutParams).marginStart = if (stopwatch.isIdle) 0 else padding / 2
        when {
            stopwatch.isIdle -> {
                secondaryAction.visibility = GONE
                bind(primaryAction, R.string.start) { StopwatchActions.start(activity) }
            }
            stopwatch.isRunning -> {
                secondaryAction.visibility = VISIBLE
                bind(secondaryAction, R.string.lap) { StopwatchActions.lap(activity) }
                bind(primaryAction, R.string.pause) { StopwatchActions.pause(activity) }
                postDelayed(tick, SECOND_MS - ms % SECOND_MS)
            }
            else -> {
                secondaryAction.visibility = VISIBLE
                bind(secondaryAction, R.string.reset) { StopwatchActions.reset(activity) }
                bind(primaryAction, R.string.resume) { StopwatchActions.start(activity) }
            }
        }
        showLaps(stopwatch)
    }

    private fun bind(button: InkButton, label: Int, action: () -> Unit) {
        button.setText(label)
        button.setOnClickListener {
            action()
            show()
        }
    }

    private fun showLaps(stopwatch: Stopwatch) {
        if (stopwatch.laps == shownLaps) return
        shownLaps = stopwatch.laps
        val lengths = stopwatch.lapLengths()
        laps.setAdapter(object : InkPagedList.Adapter {
            override val count get() = lengths.size
            override fun itemId(position: Int) = (lengths.size - position).toLong()
            override fun createView(position: Int) = lapRow(lengths.size - position, lengths[lengths.size - 1 - position], stopwatch.laps[lengths.size - 1 - position])
        })
    }

    private fun lapRow(number: Int, length: Long, total: Long) = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        setPadding(padding, dp(InkPagedList.ROW_PADDING_DP), padding, dp(InkPagedList.ROW_PADDING_DP))
        addView(InkTextView(activity).apply { text = activity.getString(R.string.lap_number, number) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(InkTextView(activity).apply {
            text = StopwatchLabels.elapsed(length, tenths = true)
            isBold = true
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(InkTextView(activity).apply {
            text = StopwatchLabels.elapsed(total, tenths = true)
            textScale = InkStyle.SECONDARY_SCALE
            gravity = Gravity.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun now() = Instant.now().toEpochMilli()

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val BIG_SCALE = 3f
        const val PADDING_DP = 16f
        const val SECOND_MS = 1000L
    }
}
