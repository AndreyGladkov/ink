package io.github.andreygladkov.inkclock.alarm

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkclock.R
import kotlin.math.roundToInt

class RingActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var time: InkTextView
    private lateinit var label: InkTextView
    private var shown: Alarm? = null
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val padding = dp(PADDING_DP)
        time = InkTextView(this).apply {
            textScale = TIME_SCALE
            isBold = true
            gravity = Gravity.CENTER_HORIZONTAL
        }
        label = InkTextView(this).apply {
            textScale = InkStyle.TITLE_SCALE
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val snooze = InkButton(this).apply {
            setText(R.string.snooze)
            textScale = InkStyle.DISPLAY_SCALE
            setOnClickListener { shown?.let { RingService.snooze(it.id) } }
        }
        val dismiss = InkButton(this).apply {
            setText(R.string.dismiss_hold)
            holdToAct(HOLD_MS) { shown?.let { RingService.dismiss(it.id) } }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(time, wide())
            addView(label, wide().apply { topMargin = dp(GAP_DP) })
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(snooze, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, SNOOZE_WEIGHT))
            addView(dismiss, wide().apply { topMargin = dp(GAP_DP * 2) })
        }
        InkWindow.setContent(this, root)
    }

    override fun onStart() {
        super.onStart()
        RingService.addListener(listener)
        render()
    }

    override fun onStop() {
        super.onStop()
        RingService.removeListener(listener)
    }

    private fun render() {
        val alarm = RingService.ringing
        if (alarm == null) {
            if (shown != null) A9Panel.clearSoon(this)
            finish()
            return
        }
        if (alarm == shown) return
        shown = alarm
        time.text = AlarmLabels(this).time(alarm.hour, alarm.minute)
        label.text = alarm.label.ifBlank { getString(R.string.alarm) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun InkButton.holdToAct(holdMs: Long, action: () -> Unit) {
        val act = Runnable(action)
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> main.postDelayed(act, holdMs)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(act)
            }
            false
        }
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 24f
        private const val GAP_DP = 8f
        private const val TIME_SCALE = 4f
        private const val SNOOZE_WEIGHT = 1.2f
        private const val HOLD_MS = 1000L

        fun intent(context: Context): Intent =
            Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
