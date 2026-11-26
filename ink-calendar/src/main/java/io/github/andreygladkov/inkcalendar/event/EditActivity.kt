package io.github.andreygladkov.inkcalendar.event

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkDateActivity
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkTimeActivity
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkcalendar.R
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.events.CalendarInfo
import io.github.andreygladkov.inkcalendar.events.EventDraft
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.EventStore
import io.github.andreygladkov.inkcalendar.events.EventWriter
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class EditActivity : Activity() {
    private val zone get() = CalendarClock.zone
    private lateinit var format: EventFormat
    private lateinit var draft: EventDraft
    private var eventId: Long? = null
    private var shownReminder: Int? = null
    private lateinit var calendars: List<CalendarInfo>
    private lateinit var titleField: InkEditText
    private lateinit var startDate: InkButton
    private lateinit var startTime: InkButton
    private lateinit var endDate: InkButton
    private lateinit var endTime: InkButton
    private lateinit var calendarButtons: List<Pair<Long, InkButton>>
    private lateinit var error: InkTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        format = EventFormat(this, zone)
        eventId = intent.getLongExtra(EXTRA_EVENT, -1).takeIf { it >= 0 }
        calendars = writableCalendars()
        val loaded = savedInstanceState?.let(::restore) ?: start() ?: return finish()
        draft = loaded
        val ready = InkButton(this).apply {
            setText(InkR.string.ready)
            setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(PADDING_DP) }
        }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@EditActivity, getString(if (eventId == null) R.string.new_event else R.string.event), ::finish, ready))
            addView(ScrollView(this@EditActivity).apply { addView(body()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        show()
    }

    private fun writableCalendars(): List<CalendarInfo> {
        val store = EventStore.of(this)
        store.writableCalendars().takeIf { it.isNotEmpty() }?.let { return it }
        EventWriter.of(this).createLocalCalendar(getString(R.string.local_calendar))
        return store.writableCalendars()
    }

    private fun start(): EventDraft? {
        val id = eventId
        if (id != null) {
            val store = EventStore(contentResolver, emptySet())
            val occurrence = store.occurrence(id, intent.getLongExtra(EXTRA_BEGIN, 0), zone) ?: return null
            shownReminder = store.reminders(id).firstOrNull()
            return EventDraft.of(occurrence, shownReminder, zone)
        }
        val calendar = EventWriter.defaultCalendar(this, calendars) ?: return null
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DATE, CalendarClock.today().toEpochDay()))
        val nextHour = LocalTime.now(CalendarClock.clock).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        val start = date.atTime(nextHour)
        return EventDraft("", allDay = false, start = start, end = start.plusHours(1), calendarId = calendar.id, reminder = null)
    }

    private fun body() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        titleField = InkEditText(this@EditActivity).apply {
            isBordered = true
            hint = getString(R.string.title_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(draft.title)
        }
        addView(titleField, wide())
        addView(InkSettings.onOff(this@EditActivity, R.string.all_day, draft.allDay) { allDay ->
            draft = draft.copy(allDay = allDay)
            show()
        }, wide())
        addSection(getString(R.string.event_start))
        startDate = pickerButton { pickDate(REQUEST_START_DATE, R.string.event_start, draft.start.toLocalDate()) }
        startTime = pickerButton { pickTime(REQUEST_START_TIME, R.string.event_start, draft.start.toLocalTime()) }
        addView(row(startDate, startTime), wide().apply { topMargin = dp(GAP_DP) })
        addSection(getString(R.string.event_end))
        endDate = pickerButton { pickDate(REQUEST_END_DATE, R.string.event_end, draft.end.toLocalDate()) }
        endTime = pickerButton { pickTime(REQUEST_END_TIME, R.string.event_end, draft.end.toLocalTime()) }
        addView(row(endDate, endTime), wide().apply { topMargin = dp(GAP_DP) })
        calendarButtons = calendars.map { calendar ->
            calendar.id to InkButton(this@EditActivity).apply {
                text = calendar.name
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    draft = draft.copy(calendarId = calendar.id)
                    show()
                }
            }
        }
        if (calendars.size > 1 && eventId == null) {
            addSection(getString(R.string.event_calendar))
            calendarButtons.forEach { (_, button) -> addView(button, wide().apply { topMargin = dp(GAP_DP) }) }
        }
        addView(InkChoiceRow(this@EditActivity, getString(R.string.event_reminder), EventDraft.REMINDERS.map { it to reminderLabel(it) }, draft.reminder) { reminder ->
            draft = draft.copy(reminder = reminder)
        }, wide())
        error = InkTextView(this@EditActivity).apply {
            textScale = InkStyle.SECONDARY_SCALE
            visibility = View.GONE
            setPadding(0, dp(GAP_DP) * 2, 0, 0)
        }
        addView(error, wide())
    }

    private fun reminderLabel(minutes: Int?) = getString(when (minutes) {
        null -> R.string.reminder_none
        0 -> R.string.reminder_short_start
        10 -> R.string.reminder_short_minutes
        60 -> R.string.reminder_short_hour
        else -> R.string.reminder_short_day
    })

    private fun pickerButton(onClick: () -> Unit) = InkButton(this).apply { setOnClickListener { onClick() } }

    private fun row(vararg buttons: InkButton) = InkButtonRow(this, dp(GAP_DP), 1f).apply { buttons.forEach(::addButton) }

    private fun show() {
        startDate.text = format.compactDay(draft.start.toLocalDate())
        endDate.text = format.compactDay(draft.end.toLocalDate())
        startTime.text = format.time(draft.start.toLocalTime())
        endTime.text = format.time(draft.end.toLocalTime())
        val times = if (draft.allDay) View.GONE else View.VISIBLE
        startTime.visibility = times
        endTime.visibility = times
        calendarButtons.forEach { (id, button) -> button.isSelected = id == draft.calendarId }
        error.visibility = View.GONE
    }

    @Suppress("DEPRECATION")
    private fun pickDate(request: Int, title: Int, date: LocalDate) =
        startActivityForResult(InkDateActivity.intent(this, getString(title), date, CalendarClock.today()), request)

    @Suppress("DEPRECATION")
    private fun pickTime(request: Int, title: Int, time: LocalTime) =
        startActivityForResult(InkTimeActivity.intent(this, getString(title), time), request)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_START_DATE -> InkDateActivity.chosen(data)?.let { moveStart(draft.start.with(it)) }
            REQUEST_START_TIME -> InkTimeActivity.chosen(data)?.let { moveStart(draft.start.with(it)) }
            REQUEST_END_DATE -> InkDateActivity.chosen(data)?.let { draft = draft.copy(end = draft.end.with(it)) }
            REQUEST_END_TIME -> InkTimeActivity.chosen(data)?.let { draft = draft.copy(end = draft.end.with(it)) }
            else -> return
        }
        show()
    }

    /** Moving the start keeps the event as long as it was, as the end most likely moves with it. */
    private fun moveStart(start: LocalDateTime) {
        val length = Duration.between(draft.start, draft.end).coerceAtLeast(Duration.ZERO)
        draft = draft.copy(start = start, end = start.plus(length))
    }

    private fun save() {
        val saving = draft.copy(title = titleField.text.toString().trim())
        if (saving.endsBeforeItStarts) return complain(R.string.end_before_start)
        val writer = EventWriter.of(this)
        val id = eventId
        if (id == null) {
            writer.insert(saving) ?: return complain(R.string.not_saved)
            EventWriter.setDefaultCalendar(this, saving.calendarId)
        } else {
            writer.update(id, saving, reminderChanged = saving.reminder != shownReminder)
        }
        val begin = if (saving.allDay) saving.start.toLocalDate().atStartOfDay(ZoneOffset.UTC) else saving.start.atZone(zone)
        setResult(RESULT_OK, Intent().putExtra(EXTRA_BEGIN, begin.toInstant().toEpochMilli()))
        finish()
    }

    private fun complain(message: Int) {
        error.setText(message)
        error.visibility = View.VISIBLE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TITLE, titleField.text.toString())
        outState.putBoolean(STATE_ALL_DAY, draft.allDay)
        outState.putString(STATE_START, draft.start.toString())
        outState.putString(STATE_END, draft.end.toString())
        outState.putLong(STATE_CALENDAR, draft.calendarId)
        draft.reminder?.let { outState.putInt(STATE_REMINDER, it) }
        shownReminder?.let { outState.putInt(STATE_SHOWN_REMINDER, it) }
    }

    private fun restore(state: Bundle): EventDraft {
        shownReminder = state.takeIf { it.containsKey(STATE_SHOWN_REMINDER) }?.getInt(STATE_SHOWN_REMINDER)
        return EventDraft(
            title = state.getString(STATE_TITLE).orEmpty(),
            allDay = state.getBoolean(STATE_ALL_DAY),
            start = LocalDateTime.parse(state.getString(STATE_START)),
            end = LocalDateTime.parse(state.getString(STATE_END)),
            calendarId = state.getLong(STATE_CALENDAR),
            reminder = state.takeIf { it.containsKey(STATE_REMINDER) }?.getInt(STATE_REMINDER),
        )
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_DATE = "date"
        private const val REQUEST_START_DATE = 1
        private const val REQUEST_START_TIME = 2
        private const val REQUEST_END_DATE = 3
        private const val REQUEST_END_TIME = 4
        private const val STATE_TITLE = "title"
        private const val STATE_ALL_DAY = "all_day"
        private const val STATE_START = "start"
        private const val STATE_END = "end"
        private const val STATE_CALENDAR = "calendar"
        private const val STATE_REMINDER = "reminder"
        private const val STATE_SHOWN_REMINDER = "shown_reminder"
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun create(context: Context, date: LocalDate): Intent = Intent(context, EditActivity::class.java).putExtra(EXTRA_DATE, date.toEpochDay())

        fun edit(context: Context, eventId: Long, begin: Long): Intent =
            Intent(context, EditActivity::class.java).putExtra(EXTRA_EVENT, eventId).putExtra(EXTRA_BEGIN, begin)

        fun savedBegin(data: Intent?): Long? = data?.takeIf { it.hasExtra(EXTRA_BEGIN) }?.getLongExtra(EXTRA_BEGIN, 0)
    }
}
