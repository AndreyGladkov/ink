package io.github.andreygladkov.inkclock.ring

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
import io.github.andreygladkov.inkclock.alarm.AlarmLabels
import io.github.andreygladkov.inkclock.timer.TimerLabels
import kotlin.math.roundToInt

class RingActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var title: InkTextView
    private lateinit var subtitle: InkTextView
    private lateinit var primary: InkButton
    private lateinit var secondary: InkButton
    private var shown: Ring? = null
    private var hold: Runnable? = null
    private val listener: () -> Unit = { render() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val padding = dp(PADDING_DP)
        title = InkTextView(this).apply {
            textScale = TITLE_SCALE
            isBold = true
            gravity = Gravity.CENTER_HORIZONTAL
        }
        subtitle = InkTextView(this).apply {
            textScale = InkStyle.TITLE_SCALE
            gravity = Gravity.CENTER_HORIZONTAL
        }
        primary = InkButton(this).apply { textScale = InkStyle.DISPLAY_SCALE }
        secondary = InkButton(this)
        secondary.setOnTouchListener(::onSecondaryTouch)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(title, wide())
            addView(subtitle, wide().apply { topMargin = dp(GAP_DP) })
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(primary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, PRIMARY_WEIGHT))
            addView(secondary, wide().apply { topMargin = dp(GAP_DP * 2) })
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
        val ring = RingService.ringing
        if (ring == null) {
            if (shown != null) A9Panel.clearSoon(this)
            finish()
            return
        }
        if (ring == shown) return
        shown = ring
        hold = null
        when (ring) {
            is Ring.OfAlarm -> {
                title.text = AlarmLabels(this).time(ring.alarm.hour, ring.alarm.minute)
                subtitle.text = ring.alarm.label.ifBlank { getString(R.string.alarm) }
                primary.setText(R.string.snooze)
                primary.setOnClickListener { RingService.snooze(ring.alarm.id) }
                secondary.setText(R.string.dismiss_hold)
                secondary.setOnClickListener(null)
                hold = Runnable { RingService.dismiss(ring.alarm.id) }
            }
            is Ring.OfTimer -> {
                title.setText(R.string.timer_done)
                subtitle.text = ring.label.ifBlank { TimerLabels(this).duration(ring.durationMs) }
                primary.setText(R.string.stop)
                primary.setOnClickListener { RingService.stopTimer() }
                secondary.setText(R.string.add_minute)
                secondary.setOnClickListener { RingService.addMinute() }
            }
        }
    }

    /** An alarm is only dismissed by holding, so a half-asleep tap cannot silence it by accident. */
    private fun onSecondaryTouch(view: View, event: MotionEvent): Boolean {
        val action = hold ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> main.postDelayed(action, HOLD_MS)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(action)
        }
        return false
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 24f
        private const val GAP_DP = 8f
        private const val TITLE_SCALE = 3f
        private const val PRIMARY_WEIGHT = 1.2f
        private const val HOLD_MS = 1000L

        fun intent(context: Context): Intent =
            Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
