package io.github.andreygladkov.inkphone.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.dialer.DialPad
import io.github.andreygladkov.inkphone.hasPermission
import kotlin.math.roundToInt

class CallActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density
    private lateinit var status: InkTextView
    private lateinit var title: InkTextView
    private lateinit var subtitle: InkTextView
    private lateinit var info: InkTextView
    private lateinit var controls: LinearLayout
    private var keypadOpen = false
    private var dtmf = StringBuilder()
    private var proximity: PowerManager.WakeLock? = null
    private var shown: CallScreenModel? = null

    private val listener: () -> Unit = { render() }
    private val tick = Runnable {
        val model = CallManager.model()
        renderStatus(model)
        scheduleTick(model)
    }
    private val finishAfterEnd = Runnable {
        CallManager.clearEnded()
        CallPanelClear.onCallScreenClosed(this)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val padding = dp(PADDING_DP)
        status = InkTextView(this).apply { textScale = InkStyle.SECONDARY_SCALE }
        title = InkTextView(this).apply {
            textScale = InkStyle.DISPLAY_SCALE
            isBold = true
        }
        subtitle = InkTextView(this)
        info = InkTextView(this).apply { textScale = InkStyle.SECONDARY_SCALE }
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(View(context), LinearLayout.LayoutParams(0, 0, SPACE_ABOVE_HEADER))
            listOf(status, title, subtitle, info).forEach {
                it.gravity = Gravity.CENTER_HORIZONTAL
                addView(it, wrap().apply { bottomMargin = dp(GAP_DP) })
            }
            addView(View(context), LinearLayout.LayoutParams(0, 0, SPACE_BELOW_HEADER))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        InkWindow.setContent(this, root)
        proximity = getSystemService(PowerManager::class.java)
            .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "InkPhone:proximity")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        answerIfAsked()
    }

    private fun answerIfAsked() {
        val callId = intent.getStringExtra(EXTRA_ANSWER) ?: return
        intent.removeExtra(EXTRA_ANSWER)
        CallManager.answer(callId)
    }

    override fun onStart() {
        super.onStart()
        answerIfAsked()
        CallManager.addListener(listener)
        render()
    }

    override fun onStop() {
        super.onStop()
        CallManager.removeListener(listener)
        main.removeCallbacks(tick)
        setProximity(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(finishAfterEnd)
    }

    private fun render() {
        val model = CallManager.model()
        renderStatus(model)
        scheduleTick(model)
        if (shown != model) renderControls(model)
        shown = model
        updateWindow(model)
    }

    private fun scheduleTick(model: CallScreenModel) {
        main.removeCallbacks(tick)
        val call = (model as? CallScreenModel.InCall)?.primary ?: return
        CallPresenter.millisUntilNextMinute(call, System.currentTimeMillis())?.let { main.postDelayed(tick, it) }
    }

    private fun renderStatus(model: CallScreenModel) {
        val (statusText, call) = when (model) {
            is CallScreenModel.Incoming -> getString(R.string.call_incoming) to model.call
            is CallScreenModel.Waiting -> getString(R.string.call_incoming) to model.incoming
            is CallScreenModel.SelectAccount -> getString(R.string.call_select_sim) to model.call
            is CallScreenModel.InCall -> phaseText(model.primary) to model.primary
            is CallScreenModel.Ended -> getString(R.string.call_ended) to model.call
            CallScreenModel.Idle -> getString(R.string.call_ended) to null
        }
        status.text = statusText
        title.text = call?.title ?: getString(R.string.call_unknown)
        subtitle.text = call?.number?.takeIf { call.name != null }.orEmpty()
        subtitle.visibility = if (subtitle.text.isEmpty()) View.GONE else View.VISIBLE
        info.text = when (model) {
            is CallScreenModel.Waiting -> getString(R.string.call_waiting_current, model.current.title ?: getString(R.string.call_unknown))
            is CallScreenModel.InCall -> when {
                keypadOpen -> dtmf.toString()
                model.held != null -> getString(R.string.call_held_line, model.held.title ?: getString(R.string.call_unknown))
                else -> ""
            }
            else -> ""
        }
        info.visibility = if (info.text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun phaseText(call: CallSnapshot): String = when (call.phase) {
        CallPhase.HOLDING -> getString(R.string.call_on_hold)
        CallPhase.ACTIVE -> CallPresenter.elapsedMinutes(call, System.currentTimeMillis())
            ?.takeIf { it > 0 }?.let { getString(R.string.call_active_minutes, it.toInt()) } ?: getString(R.string.call_active)
        CallPhase.DISCONNECTING, CallPhase.DISCONNECTED -> getString(R.string.call_ended)
        else -> getString(R.string.call_dialing)
    }

    private fun renderControls(model: CallScreenModel) {
        controls.removeAllViews()
        main.removeCallbacks(finishAfterEnd)
        when (model) {
            is CallScreenModel.Incoming -> controls.addView(row(
                button(R.string.call_decline, R.drawable.ic_call_end) { CallManager.reject(model.call.id) },
                button(R.string.call_answer, R.drawable.ic_call) { CallManager.answer(model.call.id) },
                tall = true,
            ))
            is CallScreenModel.Waiting -> {
                controls.addView(button(R.string.call_hold_and_answer, R.drawable.ic_call) { CallManager.answer(model.incoming.id) }, full())
                controls.addView(button(R.string.call_end_and_answer, R.drawable.ic_call) { CallManager.endAndAnswer(model.incoming.id, model.current.id) }, full())
                controls.addView(button(R.string.call_decline, R.drawable.ic_call_end) { CallManager.reject(model.incoming.id) }, full())
            }
            is CallScreenModel.SelectAccount -> {
                if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                    requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_PHONE_STATE)
                }
                accounts().forEach { (label, handle) ->
                    controls.addView(button(0, R.drawable.ic_call, label) { CallManager.selectAccount(model.call.id, handle) }, full())
                }
                controls.addView(button(R.string.cancel, R.drawable.ic_close) { CallManager.hangUp(model.call.id) }, full())
            }
            is CallScreenModel.InCall -> renderInCall(model)
            is CallScreenModel.Ended, CallScreenModel.Idle -> main.postDelayed(finishAfterEnd, ENDED_SHOWN_MS)
        }
    }

    private fun renderInCall(model: CallScreenModel.InCall) {
        val id = model.primary.id
        if (keypadOpen) {
            controls.addView(DialPad(this, object : DialPad.Listener {
                override fun onKeyDown(key: Char) {
                    dtmf.append(key)
                    CallManager.playDtmf(id, key)
                    renderStatus(CallManager.model())
                }

                override fun onKeyUp(key: Char) {
                    CallManager.stopDtmf(id)
                }
            }), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(KEYPAD_HEIGHT_DP)))
            controls.addView(button(R.string.call_hide_keypad, R.drawable.ic_keyboard_hide) { toggleKeypad() }, full())
        } else {
            val audio = model.audio
            val toggles = buildList {
                add(toggle(R.string.call_mute, R.drawable.ic_mic_off, audio.muted) { CallManager.setMuted(!audio.muted) })
                add(toggle(R.string.call_speaker, R.drawable.ic_volume_up, audio.route == AudioRoute.SPEAKER) {
                    CallManager.setRoute(if (audio.route == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER)
                })
                add(toggle(R.string.call_keypad, R.drawable.ic_tab_dialer, false) { toggleKeypad() })
                if (model.canHold) add(toggle(R.string.call_hold, R.drawable.ic_pause, model.primary.phase == CallPhase.HOLDING) { CallManager.toggleHold(id) })
                if (AudioRoute.BLUETOOTH in audio.supportedRoutes) add(toggle(R.string.call_bluetooth, R.drawable.ic_bluetooth, audio.route == AudioRoute.BLUETOOTH) {
                    CallManager.setRoute(if (audio.route == AudioRoute.BLUETOOTH) AudioRoute.EARPIECE else AudioRoute.BLUETOOTH)
                })
                if (model.canSwap) add(toggle(R.string.call_swap, R.drawable.ic_swap_calls, false) { CallManager.swap(model.held!!.id) })
                if (model.canMerge) add(toggle(R.string.call_merge, R.drawable.ic_call_merge, false) { CallManager.merge(id, model.held!!.id) })
            }
            toggles.chunked(2).forEach { pair -> controls.addView(row(*pair.toTypedArray())) }
        }
        controls.addView(button(R.string.call_end, R.drawable.ic_call_end) { CallManager.hangUp(id) }, full().apply { topMargin = dp(GAP_DP * 2) })
    }

    private fun toggleKeypad() {
        keypadOpen = !keypadOpen
        dtmf.clear()
        shown = null
        render()
    }

    private fun updateWindow(model: CallScreenModel) {
        val ringing = model is CallScreenModel.Incoming || model is CallScreenModel.Waiting
        if (ringing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setProximity(model is CallScreenModel.InCall && model.audio.route == AudioRoute.EARPIECE && !keypadOpen)
    }

    private fun setProximity(enabled: Boolean) {
        val lock = proximity ?: return
        if (enabled && !lock.isHeld) lock.acquire(MAX_CALL_MS)
        if (!enabled && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHONE_STATE && hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            shown = null
            render()
        }
    }

    private fun accounts(): List<Pair<String, android.telecom.PhoneAccountHandle>> {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val telecom = getSystemService(TelecomManager::class.java)
        return telecom.callCapablePhoneAccounts.map { handle ->
            (telecom.getPhoneAccount(handle)?.label?.toString() ?: handle.id) to handle
        }
    }

    private fun button(text: Int, icon: Int, label: String? = null, onClick: () -> Unit) = InkButton(this).apply {
        this.text = label ?: getString(text)
        if (icon != 0) {
            this.icon = icon
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = ICON_DP
        }
        setOnClickListener { onClick() }
    }

    private fun toggle(text: Int, icon: Int, on: Boolean, onClick: () -> Unit) = InkButton(this).apply {
        setText(text)
        textScale = InkStyle.SECONDARY_SCALE
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = ICON_DP
        isSelected = on
        setOnClickListener { onClick() }
    }

    private fun row(vararg buttons: InkButton, tall: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            if (tall) button.minHeight = dp(TALL_DP)
            addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(GAP_DP)
            })
        }
        layoutParams = full()
    }

    private fun full() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(GAP_DP)
    }

    private fun wrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        private const val REQUEST_PHONE_STATE = 44
        private const val PADDING_DP = 16f
        private const val SPACE_ABOVE_HEADER = 1f
        private const val SPACE_BELOW_HEADER = 2f
        private const val GAP_DP = 8f
        private const val TALL_DP = 96f
        private const val KEYPAD_HEIGHT_DP = 300f
        private const val ICON_DP = 24f
        private const val ENDED_SHOWN_MS = 1500L
        private const val MAX_CALL_MS = 4 * 60 * 60 * 1000L

        private const val EXTRA_ANSWER = "answer"

        fun intent(context: Context): Intent = Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun answerIntent(context: Context, callId: String): Intent = intent(context).putExtra(EXTRA_ANSWER, callId)
    }
}
