package io.github.andreygladkov.inkcalendar

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.MonthGrid
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkcalendar.event.EditActivity
import io.github.andreygladkov.inkcalendar.event.EventActivity
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.events.EventFormat
import io.github.andreygladkov.inkcalendar.events.EventStore
import io.github.andreygladkov.inkcalendar.events.Occurrence
import io.github.andreygladkov.inkcalendar.remind.Reminders
import io.github.andreygladkov.inkcalendar.settings.SettingsActivity
import io.github.andreygladkov.inkcalendar.tasks.DueTask
import io.github.andreygladkov.inkcalendar.tasks.InkTasks
import io.github.andreygladkov.inkcalendar.ui.SwipeFrame
import io.github.andreygladkov.inkcalendar.widget.WidgetUpdater
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class MainActivity : Activity() {
    private val locale get() = resources.configuration.locales[0]
    private lateinit var style: InkStyle
    private lateinit var gate: PermissionGate
    private lateinit var format: EventFormat
    private lateinit var today: LocalDate
    private lateinit var selected: LocalDate
    private lateinit var month: YearMonth
    private lateinit var monthTitle: InkButton
    private lateinit var grid: MonthGrid
    private lateinit var dayLabel: InkTextView
    private lateinit var toToday: InkButton
    private lateinit var list: InkPagedList
    private lateinit var empty: InkTextView
    private lateinit var calendar: View
    private lateinit var permission: View
    private lateinit var grant: InkButton
    private var isListening = false
    private var tasks = emptyList<DueTask>()
    private val changes = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = show()
    }
    private val taskChanges = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadTasks()
            show()
        }
    }
    private val dayChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        style = InkStyle.of(this)
        gate = PermissionGate(this, listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), REQUEST_PERMISSION)
        format = EventFormat(this)
        today = CalendarClock.today()
        selected = savedInstanceState?.getLong(STATE_SELECTED)?.let(LocalDate::ofEpochDay) ?: today
        month = YearMonth.from(selected)
        monthTitle = InkButton(this).apply {
            isBordered = false
            isBold = true
            textScale = InkStyle.TITLE_SCALE
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            contentDescription = getString(R.string.choose_year)
            setOnClickListener { openYear() }
        }
        grid = MonthGrid(this).apply { onDay = ::select }
        dayLabel = InkTextView(this).apply {
            isBold = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(PADDING_DP), 0, dp(PADDING_DP), 0)
        }
        toToday = InkButton(this).apply {
            setText(R.string.today)
            isBordered = false
            setOnClickListener { goToday() }
        }
        list = InkPagedList(this)
        empty = InkTextView(this).apply {
            setText(R.string.no_events)
            gravity = Gravity.CENTER
        }
        grant = InkButton(this)
        calendar = calendarBody()
        permission = permissionBody()
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@MainActivity, monthTitle,
                InkHeader.button(this@MainActivity, InkR.drawable.ic_chevron_left, getString(InkR.string.prev_month)) { step(-1) },
                InkHeader.button(this@MainActivity, InkR.drawable.ic_chevron_right, getString(InkR.string.next_month)) { step(1) },
                InkHeader.settings(this@MainActivity) { startActivity(SettingsActivity.intent(this@MainActivity)) }))
            addView(FrameLayout(this@MainActivity).apply {
                addView(calendar)
                addView(permission)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    private fun calendarBody() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(SwipeFrame(this@MainActivity, ::step).apply { addView(grid) }, wide())
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(dayLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toToday)
            addView(InkHeader.button(this@MainActivity, R.drawable.ic_add, getString(R.string.new_event)) {
                startActivity(EditActivity.create(this@MainActivity, selected))
            })
        }, wide())
        addView(InkHeader.line(this@MainActivity))
        addView(FrameLayout(this@MainActivity).apply {
            addView(list)
            addView(empty)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun permissionBody() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        addView(InkTextView(this@MainActivity).apply {
            setText(R.string.permission_explain)
            gravity = Gravity.CENTER
        })
        addView(grant, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = padding
        })
    }

    override fun onResume() {
        super.onResume()
        if (InkStyle.of(this) != style) return recreate()
        listen()
        val newToday = CalendarClock.today()
        if (newToday != today && selected == today) {
            selected = newToday
            month = YearMonth.from(newToday)
        }
        today = newToday
        WidgetUpdater.noticeTasksAccess(this)
        loadTasks()
        show()
    }

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    private fun listen() {
        if (gate.isGranted) contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, changes)
        if (InkTasks(this).canRead) listenToTasks()
        ContextCompat.registerReceiver(this, dayChanges, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        isListening = true
    }

    /** Ink Tasks may be gone by now, and an observer of a provider that does not exist is refused. */
    private fun listenToTasks() {
        try {
            contentResolver.registerContentObserver(InkTasks.URI, false, taskChanges)
        } catch (_: SecurityException) {
        }
    }

    /** Android still pauses an activity that called [recreate] from [onResume], before it started listening. */
    private fun stopListening() {
        if (!isListening) return
        contentResolver.unregisterContentObserver(changes)
        contentResolver.unregisterContentObserver(taskChanges)
        unregisterReceiver(dayChanges)
        isListening = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_SELECTED, selected.toEpochDay())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_YEAR) YearActivity.chosen(data)?.let(::open)
    }

    private fun show() {
        today = CalendarClock.today()
        val granted = gate.isGranted
        calendar.visibility = if (granted) View.VISIBLE else View.GONE
        permission.visibility = if (granted) View.GONE else View.VISIBLE
        monthTitle.text = MonthGrid.title(month, locale)
        if (!granted) return gate.bind(grant)
        askOnce()
        val zone = CalendarClock.zone
        val first = MonthGrid.firstShown(month, MonthGrid.firstDayOfWeek(locale))
        val until = first.plusDays((MonthGrid.WEEKS * MonthGrid.DAYS_IN_WEEK).toLong())
        val occurrences = EventStore.of(this).occurrences(first, until, zone)
        grid.show(month, today, selected, occurrences.flatMap { it.days(zone) }.toSet() + tasks.map { it.due })
        dayLabel.text = format.day(selected)
        toToday.visibility = if (selected == today) View.INVISIBLE else View.VISIBLE
        val rows = Occurrence.onDay(occurrences, selected, zone).map(::row) + DueTask.onDay(tasks, selected).map(::row)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = rows.size
            override fun itemId(position: Int) = position.toLong()
            override fun createView(position: Int) = rows[position]()
        })
    }

    /** Ink Tasks is read once per visit and on its changes rather than on every tap, since it reads all its lists to answer. */
    private fun loadTasks() {
        tasks = InkTasks(this).due()
    }

    /**
     * Notifications for reminders and access to Ink Tasks are asked for once, together, since a second request made while
     * the first dialog is up is dropped. The calendar works without either.
     */
    private fun askOnce() {
        val prefs = inkPrefs()
        val inkTasks = InkTasks(this)
        if (!inkTasks.isInstalled) prefs.edit { remove(PREF_TASKS_ASKED) }
        val asking = listOfNotNull(
            Manifest.permission.POST_NOTIFICATIONS.takeIf {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Reminders.isOn(this) && !prefs.getBoolean(PREF_NOTIFICATIONS_ASKED, false)
            },
            InkTasks.PERMISSION.takeIf { inkTasks.isOn && inkTasks.isInstalled && !prefs.getBoolean(PREF_TASKS_ASKED, false) },
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (asking.isEmpty()) return
        prefs.edit {
            if (Manifest.permission.POST_NOTIFICATIONS in asking) putBoolean(PREF_NOTIFICATIONS_ASKED, true)
            if (InkTasks.PERMISSION in asking) putBoolean(PREF_TASKS_ASKED, true)
        }
        requestPermissions(asking.toTypedArray(), REQUEST_ONCE)
    }

    private fun row(occurrence: Occurrence): () -> View = {
        row(twoLines(occurrence.title.ifBlank { getString(R.string.no_title) }, format.timeOn(occurrence, selected))) {
            startActivity(EventActivity.intent(this, occurrence.eventId, occurrence.begin))
        }
    }

    private fun row(task: DueTask): () -> View = {
        val detail = listOfNotNull(getString(R.string.task), task.list, task.time?.let(format::time)).joinToString(TASK_DETAIL_SEPARATOR)
        row(twoLines(task.title.ifBlank { getString(R.string.no_title) }, detail)) { openTask(task) }.apply {
            icon = R.drawable.ic_check_box_outline
            iconPosition = InkButton.IconPosition.START
            iconSizeDp = TASK_BOX_DP
        }
    }

    private fun row(label: CharSequence, onClick: () -> Unit) = InkButton(this).apply {
        text = label
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        maxLines = MAX_LINES
        ellipsize = TextUtils.TruncateAt.END
        setOnClickListener { onClick() }
    }

    /** Ink Tasks may have been removed since the list was drawn. */
    private fun openTask(task: DueTask) {
        try {
            startActivity(InkTasks.open(task))
        } catch (_: ActivityNotFoundException) {
            loadTasks()
            show()
        } catch (_: SecurityException) {
            loadTasks()
            show()
        }
    }

    private fun select(date: LocalDate) {
        selected = date
        show()
    }

    private fun step(months: Int) = open(month.plusMonths(months.toLong()))

    private fun open(target: YearMonth) {
        month = target
        selected = if (target == YearMonth.from(today)) today else target.atDay(1)
        show()
    }

    private fun goToday() = open(YearMonth.from(today))

    @Suppress("DEPRECATION")
    private fun openYear() = startActivityForResult(YearActivity.intent(this, month.year), REQUEST_YEAR)

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val REQUEST_PERMISSION = 1
        const val REQUEST_YEAR = 2
        const val REQUEST_ONCE = 3
        const val PREF_NOTIFICATIONS_ASKED = "notifications_asked"
        const val PREF_TASKS_ASKED = "ink_tasks_asked"
        const val TASK_DETAIL_SEPARATOR = " · "
        const val TASK_BOX_DP = 24f
        const val STATE_SELECTED = "selected"
        const val PADDING_DP = 16f
        const val MAX_LINES = 4
    }
}
