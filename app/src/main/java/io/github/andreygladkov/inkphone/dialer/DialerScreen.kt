package io.github.andreygladkov.inkphone.dialer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.TabScreen
import io.github.andreygladkov.inkphone.call.CallLauncher
import io.github.andreygladkov.inkphone.contacts.ContactEntry
import io.github.andreygladkov.inkphone.contacts.ContactList
import io.github.andreygladkov.inkphone.contacts.ContactListOptions
import io.github.andreygladkov.inkphone.contacts.ContactsRepository
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.favorites.SpeedDial
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.settings.SettingsActivity
import io.github.andreygladkov.inkphone.ui.DialPad
import io.github.andreygladkov.inkphone.ui.DottedDrawable
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkTextView
import io.github.andreygladkov.inkphone.ui.twoLines
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class DialerScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val number = StringBuilder()
    private var contacts: List<ContactEntry> = emptyList()
    private var tones: ToneGenerator? = null

    private val numberView = InkTextView(activity).apply {
        textScale = activity.inkPrefs().getFloat(PREF_NUMBER_SIZE_SP, DEFAULT_NUMBER_SIZE_SP) / style.textSizeSp
        isBold = true
        gravity = Gravity.CENTER
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.START
    }
    private val backspace = InkButton(activity).apply {
        icon = R.drawable.ic_backspace
        contentDescription = context.getString(R.string.delete)
        isBordered = false
        setOnClickListener { edit { if (it.isNotEmpty()) it.deleteCharAt(it.lastIndex) } }
        setOnLongClickListener {
            edit { it.clear() }
            true
        }
    }
    private val suggestions = LinearLayout(activity).apply { orientation = VERTICAL }
    private val banner = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val padding = dp(GAP_DP * 2)
        setPadding(padding, padding, padding, padding)
        addView(InkTextView(activity).apply {
            setText(R.string.default_dialer_rationale)
            textScale = SMALL_SCALE
            gravity = Gravity.CENTER
        })
        addView(InkButton(activity).apply {
            setText(R.string.make_default)
            setOnClickListener { CallLauncher.requestDefaultDialer(activity) }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(GAP_DP)
        })
    }

    init {
        orientation = VERTICAL
        val gap = dp(GAP_DP)
        setPadding(gap, gap, gap, gap)
        addView(banner, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(suggestions, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(View(activity).apply { setBackgroundColor(InkStyle.of(activity).foreground) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(LINE_DP).coerceAtLeast(1)))
        addView(LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(InkButton(activity).apply {
                icon = R.drawable.ic_settings
                contentDescription = context.getString(R.string.settings)
                isBordered = false
                setOnClickListener { activity.startActivity(Intent(activity, SettingsActivity::class.java)) }
            })
            addView(numberView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(backspace)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(GAP_DP) })
        addView(DialPad(activity, keypadListener()), LayoutParams(LayoutParams.MATCH_PARENT, dp(KEYPAD_HEIGHT_DP)))
        addView(InkButton(activity).apply {
            setText(R.string.call)
            icon = R.drawable.ic_call
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = CALL_ICON_DP
            setOnClickListener { if (number.isNotEmpty()) CallLauncher.call(activity, number.toString()) }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tones?.release()
        tones = null
    }

    override fun onResume() = refresh()

    override fun onPermissionsResult() = refresh()

    fun setNumber(value: String) = edit {
        it.clear()
        it.append(value)
    }

    private fun refresh() {
        val isDefault = CallLauncher.isDefaultDialer(activity)
        banner.visibility = if (isDefault) GONE else VISIBLE
        suggestions.visibility = if (isDefault) VISIBLE else GONE
        if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val options = ContactListOptions.from(activity.inkPrefs()).copy(onlyWithPhone = true)
        val repository = ContactsRepository(activity.contentResolver)
        loader.execute {
            val loaded = ContactList.build(repository.loadSummaries(), options)
            main.post {
                contacts = loaded
                renderSuggestions()
            }
        }
    }

    private fun keypadListener() = object : DialPad.Listener {
        override fun onKeyDown(key: Char) {
            playTone(key)
            edit { it.append(key) }
        }

        override fun onKeyUp(key: Char) {
            tones?.stopTone()
        }

        override fun onKeyLongPress(key: Char): Boolean = when (key) {
            '0' -> {
                edit { it.setCharAt(it.lastIndex, '+') }
                true
            }
            '1' -> {
                if (number.toString() == "1") {
                    edit { it.clear() }
                    CallLauncher.callVoicemail(activity)
                }
                true
            }
            in SpeedDial.KEYS -> {
                if (number.toString() == key.toString()) speedDial(key)
                true
            }
            else -> false
        }
    }

    private fun speedDial(key: Char) {
        val assigned = SpeedDial(activity.inkPrefs()).numberFor(key)
        edit { it.clear() }
        if (assigned != null) {
            CallLauncher.call(activity, assigned)
        } else {
            suggestions.removeAllViews()
            suggestions.addView(InkTextView(activity).apply {
                text = context.getString(R.string.speed_dial_not_set, key.toString())
                textScale = SMALL_SCALE
                gravity = Gravity.CENTER
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    private fun edit(change: (StringBuilder) -> Unit) {
        change(number)
        render()
    }

    private fun render() {
        numberView.text = number.ifEmpty { "" }
        numberView.hint = context.getString(R.string.dialer_hint)
        backspace.visibility = if (number.isEmpty()) INVISIBLE else VISIBLE
        renderSuggestions()
    }

    private fun renderSuggestions() {
        suggestions.removeAllViews()
        if (number.isEmpty() || suggestions.visibility != VISIBLE || !isAttachedToWindow) return
        if (suggestions.height == 0) {
            suggestions.post { renderSuggestions() }
            return
        }
        var used = 0
        T9.suggest(number.toString(), contacts).take(MAX_SUGGESTIONS).forEachIndexed { index, suggestion ->
            val divider = if (index > 0) View(activity).apply { background = DottedDrawable(context) } else null
            val row = suggestionRow(suggestion)
            row.measure(
                MeasureSpec.makeMeasureSpec(suggestions.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            val dividerHeight = divider?.background?.intrinsicHeight ?: 0
            if (used + dividerHeight + row.measuredHeight > suggestions.height) return
            used += dividerHeight + row.measuredHeight
            divider?.let { suggestions.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, dividerHeight)) }
            suggestions.addView(row)
        }
    }

    private fun suggestionRow(suggestion: Suggestion) = InkButton(activity).apply {
        text = twoLines(suggestion.entry.displayName, suggestion.number)
        textScale = SMALL_SCALE
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        minHeight = 0
        val vertical = dp(SUGGESTION_PADDING_DP)
        setPadding(paddingLeft, vertical, paddingRight, vertical)
        setOnClickListener { setNumber(suggestion.number) }
    }

    private fun playTone(key: Char) {
        if (Settings.System.getInt(activity.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 0) return
        val tone = TONES[key] ?: return
        val generator = tones ?: runCatching { ToneGenerator(AudioManager.STREAM_DTMF, TONE_VOLUME) }.getOrNull()?.also { tones = it }
        generator?.startTone(tone, TONE_MS)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        const val PREF_NUMBER_SIZE_SP = "dialer_number_size_sp"
        const val DEFAULT_NUMBER_SIZE_SP = 24f
        val NUMBER_SIZES_SP = listOf(20f, 24f, 28f, 32f)
        private const val GAP_DP = 8f
        private const val LINE_DP = 2f
        private const val KEYPAD_HEIGHT_DP = 240f
        private const val CALL_ICON_DP = 24f
        private const val SMALL_SCALE = 0.9f
        private const val SUGGESTION_PADDING_DP = 8f
        private const val MAX_SUGGESTIONS = 4
        private const val TONE_VOLUME = 80
        private const val TONE_MS = 150
        private val TONES = mapOf(
            '0' to ToneGenerator.TONE_DTMF_0, '1' to ToneGenerator.TONE_DTMF_1, '2' to ToneGenerator.TONE_DTMF_2,
            '3' to ToneGenerator.TONE_DTMF_3, '4' to ToneGenerator.TONE_DTMF_4, '5' to ToneGenerator.TONE_DTMF_5,
            '6' to ToneGenerator.TONE_DTMF_6, '7' to ToneGenerator.TONE_DTMF_7, '8' to ToneGenerator.TONE_DTMF_8,
            '9' to ToneGenerator.TONE_DTMF_9, '*' to ToneGenerator.TONE_DTMF_S, '#' to ToneGenerator.TONE_DTMF_P,
        )
        private val loader = Executors.newSingleThreadExecutor()
    }
}
