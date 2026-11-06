package io.github.andreygladkov.inkclock.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.net.toUri
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkclock.R
import io.github.andreygladkov.inkclock.ui.DigitEntry
import io.github.andreygladkov.inkclock.ui.DigitKeypad
import java.time.DayOfWeek
import kotlin.math.roundToInt

class AlarmEditActivity : Activity() {
    private lateinit var labels: AlarmLabels
    private lateinit var original: Alarm
    private var isNew = false
    private lateinit var time: AlarmTime
    private var days = emptySet<DayOfWeek>()
    private var ringtone: Ringtone = Ringtone.Default
    private var vibrates = true
    private lateinit var timeView: InkTextView
    private lateinit var timeError: InkTextView
    private lateinit var labelField: InkEditText
    private lateinit var ringtoneButton: InkButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        labels = AlarmLabels(this)
        val store = AlarmStore.of(this)
        val existing = intent.getIntExtra(EXTRA_ALARM_ID, -1).takeIf { it >= 0 }?.let(store::get)
        isNew = existing == null
        original = existing ?: Alarm(
            id = store.nextId(),
            hour = intent.getIntExtra(EXTRA_HOUR, DEFAULT_HOUR),
            minute = intent.getIntExtra(EXTRA_MINUTE, 0),
            label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
        )
        time = AlarmTime.of(original.hour, original.minute, labels.is24Hour)
        days = original.days
        ringtone = original.ringtone
        vibrates = original.vibrates
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@AlarmEditActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body())
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        showTime()
        showRingtone()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RINGTONE || resultCode != RESULT_OK || data == null) return
        @Suppress("DEPRECATION")
        val picked = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ringtone = when (picked) {
            null -> Ringtone.Silent
            Settings.System.DEFAULT_ALARM_ALERT_URI -> Ringtone.Default
            else -> Ringtone.Sound(picked.toString())
        }
        showRingtone()
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@AlarmEditActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        addView(InkTextView(this@AlarmEditActivity).apply {
            setText(if (isNew) R.string.new_alarm else R.string.alarm)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(InkButton(this@AlarmEditActivity).apply {
            setText(R.string.save)
            setOnClickListener { save() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(PADDING_DP) })
    }

    private fun body() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, 0, padding, padding)
        timeView = InkTextView(this@AlarmEditActivity).apply {
            textScale = TIME_SCALE
            isBold = true
            gravity = Gravity.CENTER_HORIZONTAL
        }
        addView(timeView, wide())
        timeError = InkTextView(this@AlarmEditActivity).apply {
            setText(R.string.time_invalid)
            textScale = InkStyle.SECONDARY_SCALE
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.INVISIBLE
        }
        addView(timeError, wide())
        if (!time.is24Hour) addView(InkChoiceRow(this@AlarmEditActivity, getString(R.string.half_of_day),
            listOf(false to getString(R.string.am), true to getString(R.string.pm)), time.isPm) { pm -> time = time.copy(isPm = pm) })
        addView(DigitKeypad(this@AlarmEditActivity, onKey = { time = time.type(it); showTime() }, onErase = { time = time.erase(); showTime() }),
            wide().apply { topMargin = dp(GAP_DP) })
        addSection(getString(R.string.repeat))
        addView(dayButtons(), wide().apply { topMargin = dp(GAP_DP) })
        addSection(getString(R.string.label))
        labelField = InkEditText(this@AlarmEditActivity).apply {
            isBordered = true
            setText(original.label)
            hint = getString(R.string.label_hint)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        addView(labelField, wide().apply { topMargin = dp(GAP_DP) })
        addSection(getString(R.string.sound))
        ringtoneButton = InkButton(this@AlarmEditActivity).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { pickRingtone() }
        }
        addView(ringtoneButton, wide().apply { topMargin = dp(GAP_DP) })
        addView(InkSettings.onOff(this@AlarmEditActivity, R.string.vibrate, vibrates) { vibrates = it })
        if (!isNew) addView(InkButton(this@AlarmEditActivity).apply {
            setText(R.string.delete)
            icon = R.drawable.ic_delete
            iconPosition = InkButton.IconPosition.START
            confirmBeforeAction(R.string.delete_confirm) {
                AlarmActions.delete(this@AlarmEditActivity, original.id)
                finish()
            }
        }, wide().apply { topMargin = dp(SECTION_GAP_DP) })
    }

    private fun dayButtons(): View {
        val row = InkButtonRow(this, dp(DAY_GAP_DP), InkStyle.SECONDARY_SCALE)
        labels.week.forEach { day ->
            row.addButton(InkButton(this).apply {
                text = labels.day(day)
                setPadding(0, paddingTop, 0, paddingBottom)
                isSelected = day in days
                setOnClickListener {
                    days = if (day in days) days - day else days + day
                    isSelected = day in days
                }
            })
        }
        return row
    }

    private fun showTime() {
        timeView.text = time.display()
        timeError.visibility = View.INVISIBLE
    }

    private fun showRingtone() {
        val name = when (val current = ringtone) {
            Ringtone.Default -> getString(R.string.ringtone_default)
            Ringtone.Silent -> getString(R.string.ringtone_silent)
            is Ringtone.Sound -> runCatching { RingtoneManager.getRingtone(this, current.uri.toUri())?.getTitle(this) }.getOrNull()
                ?: getString(R.string.ringtone_other)
        }
        ringtoneButton.text = name
    }

    @Suppress("DEPRECATION")
    private fun pickRingtone() {
        val existing = when (val current = ringtone) {
            Ringtone.Default -> Settings.System.DEFAULT_ALARM_ALERT_URI
            Ringtone.Silent -> null
            is Ringtone.Sound -> current.uri.toUri()
        }
        startActivityForResult(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing), REQUEST_RINGTONE)
    }

    private fun save() {
        val (hour, minute) = time.toHourMinute() ?: run {
            timeError.visibility = View.VISIBLE
            return
        }
        AlarmActions.save(this, original.copy(
            hour = hour,
            minute = minute,
            days = days,
            label = labelField.text.toString().trim(),
            ringtone = ringtone,
            vibrates = vibrates,
            isEnabled = true,
        ))
        finish()
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_LABEL = "label"
        private const val REQUEST_RINGTONE = 1
        private const val DEFAULT_HOUR = 7
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f
        private const val DAY_GAP_DP = 4f
        private const val SECTION_GAP_DP = 24f
        private const val TIME_SCALE = 3f

        fun edit(context: Context, alarmId: Int): Intent = Intent(context, AlarmEditActivity::class.java).putExtra(EXTRA_ALARM_ID, alarmId)

        fun create(context: Context, hour: Int? = null, minute: Int? = null, label: String? = null): Intent =
            Intent(context, AlarmEditActivity::class.java).apply {
                hour?.let { putExtra(EXTRA_HOUR, it) }
                minute?.let { putExtra(EXTRA_MINUTE, it) }
                label?.let { putExtra(EXTRA_LABEL, it) }
            }
    }
}

data class AlarmTime(val entry: DigitEntry, val is24Hour: Boolean, val isPm: Boolean, val isFresh: Boolean) {
    fun type(key: String) = copy(entry = (if (isFresh) entry.copy(digits = "") else entry).type(key), isFresh = false)

    fun erase() = copy(entry = if (isFresh) entry.copy(digits = "") else entry.erase(), isFresh = false)

    fun display(): String = entry.groups().let { (hours, minutes) -> "%02d:%02d".format(hours, minutes) }

    fun toHourMinute(): Pair<Int, Int>? {
        val (hours, minutes) = entry.groups()
        if (minutes > 59) return null
        return when {
            is24Hour -> hours.takeIf { it <= 23 }?.let { it to minutes }
            hours in 1..12 -> (hours % 12 + if (isPm) 12 else 0) to minutes
            else -> null
        }
    }

    companion object {
        fun of(hour: Int, minute: Int, is24Hour: Boolean): AlarmTime {
            val shown = if (is24Hour) hour else (hour % 12).takeIf { it != 0 } ?: 12
            return AlarmTime(DigitEntry.of(4, shown, minute), is24Hour, isPm = hour >= 12, isFresh = true)
        }
    }
}
