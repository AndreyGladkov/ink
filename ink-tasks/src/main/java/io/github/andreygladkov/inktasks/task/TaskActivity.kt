package io.github.andreygladkov.inktasks.task

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkDateActivity
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkTimeActivity
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inktasks.MainActivity
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.remind.ReminderNotifications
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.tasks.Task
import io.github.andreygladkov.inktasks.tasks.TaskFile
import io.github.andreygladkov.inktasks.tasks.TaskRef
import io.github.andreygladkov.inktasks.ui.DueLabels
import io.github.andreygladkov.inktasks.ui.TaskRows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

/** Each field is written to the file when it is left, so there is no Save button and nothing is lost on Back. */
class TaskActivity : Activity() {
    private val lists get() = TaskLists.of(this)
    private lateinit var list: String
    private var ref: TaskRef? = null
    private var parent: TaskRef? = null
    private var task: Task? = null
    private var newDue: LocalDate? = null
    private var newTime: LocalTime? = null
    private var isClosed = false
    private lateinit var labels: DueLabels
    private lateinit var titleField: InkEditText
    private lateinit var notesField: InkEditText
    private lateinit var dueLabel: InkTextView
    private lateinit var quickDue: List<Pair<LocalDate, InkButton>>
    private lateinit var reminderSection: View
    private lateinit var reminderLabel: InkTextView
    private lateinit var timeButton: InkButton
    private lateinit var removeReminderButton: InkButton
    private lateinit var notificationsOff: View
    private lateinit var allowNotifications: InkButton
    private val notificationGate by lazy { PermissionGate(this, Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS) }
    private lateinit var subtasks: LinearLayout
    private lateinit var subtaskSection: View
    private lateinit var doneButton: InkButton
    private lateinit var moveButton: InkButton
    private lateinit var deleteButton: InkButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = intent.getStringExtra(EXTRA_LIST) ?: return finish()
        ref = refOf(intent, EXTRA_LINE, EXTRA_OCCURRENCE)
        parent = refOf(intent, EXTRA_PARENT_LINE, EXTRA_PARENT_OCCURRENCE)
        labels = DueLabels(this, TaskLists.today())
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@TaskActivity, getString(headerTitle()), ::finish))
            addView(ScrollView(this@TaskActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body())
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        if (ref == null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            titleField.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onPause() {
        super.onPause()
        saveFields()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_DATE -> InkDateActivity.chosen(data)?.let(::setDue)
            REQUEST_TIME -> InkTimeActivity.chosen(data)?.let(::setReminderTime)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) show()
    }

    private fun headerTitle() = when {
        ref != null -> R.string.task
        parent != null -> R.string.new_subtask
        else -> R.string.new_task
    }

    private fun body() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        titleField = InkEditText(this@TaskActivity).apply {
            isBordered = true
            hint = getString(R.string.title_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnFocusChangeListener { _, focused -> if (!focused) saveFields() }
            setOnEditorActionListener { _, _, _ ->
                saveFields()
                false
            }
        }
        addView(titleField, wide())
        addSection(getString(R.string.notes))
        notesField = InkEditText(this@TaskActivity).apply {
            isBordered = true
            isSingleLine = false
            hint = getString(R.string.notes_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minLines = NOTES_MIN_LINES
            gravity = Gravity.TOP or Gravity.START
            setOnFocusChangeListener { _, focused -> if (!focused) saveFields() }
        }
        addView(notesField, wide().apply { topMargin = dp(GAP_DP) })
        addSection(getString(R.string.due))
        dueLabel = InkTextView(this@TaskActivity).apply { setPadding(0, dp(GAP_DP), 0, dp(GAP_DP)) }
        addView(dueLabel, wide())
        addView(dueButtons(), wide())
        reminderSection = reminderSection()
        addView(reminderSection, wide())
        subtaskSection = LinearLayout(this@TaskActivity).apply {
            orientation = LinearLayout.VERTICAL
            addSection(getString(R.string.subtasks))
            subtasks = LinearLayout(this@TaskActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(subtasks, wide())
            addView(action(R.string.add_subtask, R.drawable.ic_add) { addSubtask() }, wide().apply { topMargin = dp(GAP_DP) })
        }
        addView(subtaskSection, wide())
        doneButton = action(R.string.mark_done, R.drawable.ic_check_box) { toggleDone() }
        addView(doneButton, wide().apply { topMargin = dp(SECTION_GAP_DP) })
        moveButton = action(R.string.move, 0) { move() }
        addView(moveButton, wide().apply { topMargin = dp(GAP_DP) })
        deleteButton = action(R.string.delete, R.drawable.ic_delete) {}.apply { confirmBeforeAction(R.string.delete_confirm) { delete() } }
        addView(deleteButton, wide().apply { topMargin = dp(GAP_DP) })
    }

    private fun dueButtons(): View {
        val today = labels.today
        quickDue = listOf(today to R.string.today, today.plusDays(1) to R.string.tomorrow, today.plusWeeks(1) to R.string.in_a_week).map { (date, label) ->
            date to InkButton(this).apply {
                setText(label)
                setOnClickListener { setDue(date) }
            }
        }
        val gap = dp(BUTTON_GAP_DP)
        val quick = InkButtonRow(this, gap, InkStyle.SECONDARY_SCALE).apply { quickDue.forEach { addButton(it.second) } }
        val other = InkButtonRow(this, gap, InkStyle.SECONDARY_SCALE).apply {
            addButton(InkButton(this@TaskActivity).apply {
                setText(R.string.other_date)
                setOnClickListener { pickDate() }
            })
            addButton(InkButton(this@TaskActivity).apply {
                setText(R.string.remove_due)
                setOnClickListener { setDue(null) }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(quick, wide())
            addView(other, wide().apply { topMargin = gap })
        }
    }

    private fun reminderSection() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addSection(getString(R.string.reminder))
        reminderLabel = InkTextView(this@TaskActivity).apply { setPadding(0, dp(GAP_DP), 0, dp(GAP_DP)) }
        addView(reminderLabel, wide())
        timeButton = InkButton(this@TaskActivity).apply { setOnClickListener { pickTime() } }
        removeReminderButton = InkButton(this@TaskActivity).apply {
            setText(R.string.remove_reminder)
            setOnClickListener { removeReminder() }
        }
        addView(InkButtonRow(this@TaskActivity, dp(BUTTON_GAP_DP), InkStyle.SECONDARY_SCALE).apply {
            addButton(timeButton)
            addButton(removeReminderButton)
        }, wide())
        allowNotifications = InkButton(this@TaskActivity)
        notificationsOff = LinearLayout(this@TaskActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkTextView(this@TaskActivity).apply {
                setText(R.string.notifications_off)
                textScale = InkStyle.SECONDARY_SCALE
                setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
            }, wide())
            addView(allowNotifications, wide())
        }
        addView(notificationsOff, wide())
    }

    private fun action(label: Int, icon: Int, onClick: () -> Unit) = InkButton(this).apply {
        setText(label)
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun reload() {
        if (isClosed) return
        val current = ref
        if (current != null) {
            task = lists.read(list).find(current) ?: return close()
        }
        show()
    }

    private fun show() {
        val task = task
        if (task != null) {
            if (titleField.text.toString().trim() != task.title) titleField.setText(task.title)
            if (notesField.text.toString().trimEnd() != task.notes) notesField.setText(task.notes)
        }
        val due = task?.due ?: newDue.takeIf { task == null }
        dueLabel.text = due?.let(labels::withDate) ?: getString(R.string.no_due)
        quickDue.forEach { (date, button) -> button.isSelected = date == due }
        showReminder(due, task?.reminder ?: newReminder().takeIf { task == null })
        val file = lists.read(list)
        val isTop = task == null && parent == null || file.tasks.any { it.ref == ref }
        subtaskSection.visibility = if (isTop) View.VISIBLE else View.GONE
        showSubtasks(task?.subtasks.orEmpty())
        doneButton.visibility = if (task != null) View.VISIBLE else View.GONE
        doneButton.setText(if (task?.isDone == true) R.string.mark_open else R.string.mark_done)
        doneButton.icon = if (task?.isDone == true) R.drawable.ic_check_box_outline else R.drawable.ic_check_box
        moveButton.visibility = if (task != null && canMove(task, file)) View.VISIBLE else View.GONE
        deleteButton.visibility = doneButton.visibility
    }

    private fun showReminder(due: LocalDate?, reminder: LocalDateTime?) {
        reminderSection.visibility = if (due != null || reminder != null) View.VISIBLE else View.GONE
        reminderLabel.text = reminder?.let { labels.reminder(it, due) } ?: getString(R.string.no_reminder)
        timeButton.setText(if (reminder == null) R.string.set_time else R.string.change_time)
        removeReminderButton.visibility = if (reminder != null) View.VISIBLE else View.GONE
        val notifying = ReminderNotifications.canShow(this)
        notificationsOff.visibility = if (reminder != null && !notifying) View.VISIBLE else View.GONE
        if (notifying) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGate.isGranted) {
            notificationGate.bind(allowNotifications)
        } else {
            allowNotifications.setText(InkR.string.open_settings)
            allowNotifications.setOnClickListener {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
    }

    private fun newReminder(): LocalDateTime? = newTime?.let { time -> newDue?.atTime(time) }

    private fun canMove(task: Task, file: TaskFile): Boolean {
        val open = file.tasks.filter { !it.isDone }
        return !task.isDone && open.any { it.ref == task.ref } && open.size > 1 && lists.sort(list) == TaskLists.Sort.MANUAL
    }

    private fun showSubtasks(tasks: List<Task>) {
        subtasks.removeAllViews()
        val rows = TaskRows(this, labels, onCheck = { sub ->
            saveFields()
            lists.change(list) { it.setDone(sub.ref, !sub.isDone, labels.today) }
            reload()
        }, onOpen = { sub ->
            saveFields()
            startActivity(edit(this, list, sub.ref))
        })
        tasks.forEach { subtasks.addView(rows.row(it), wide()) }
    }

    private fun saveFields() {
        if (isClosed) return
        val title = titleField.text.toString().trim()
        val notes = notesField.text.toString().trimEnd()
        val current = ref
        if (current == null) {
            if (title.isEmpty()) return
            val parent = parent
            val reminder = newReminder()
            apply { file -> if (parent != null) file.addSubtask(parent, title, newDue, notes, reminder) else file.add(title, newDue, notes, reminder) }
            return
        }
        val shown = task ?: return
        if (title.isNotEmpty() && title != shown.title) apply { it.setTitle(current, title) }
        val now = ref ?: return
        if (notes != shown.notes) apply { it.setNotes(now, notes) }
    }

    private fun apply(change: (TaskFile) -> TaskFile.Change?) {
        val changed = lists.change(list, change) ?: return close()
        ref = changed.ref
        task = changed.ref?.let(changed.file::find)
    }

    private fun setDue(date: LocalDate?) {
        saveFields()
        val current = ref
        if (current == null) {
            newDue = date
            if (date == null) newTime = null
        } else {
            apply { it.setDue(current, date) }
        }
        show()
    }

    @Suppress("DEPRECATION")
    private fun pickTime() {
        val reminder = task?.reminder ?: newReminder().takeIf { task == null }
        startActivityForResult(InkTimeActivity.intent(this, getString(R.string.reminder), reminder?.toLocalTime() ?: newTime), REQUEST_TIME)
    }

    private fun setReminderTime(time: LocalTime) {
        saveFields()
        val current = ref
        if (current == null) {
            newTime = time
        } else {
            val day = task?.reminder?.toLocalDate() ?: task?.due ?: return show()
            apply { it.setReminder(current, day.atTime(time)) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationGate.request()
        show()
    }

    private fun removeReminder() {
        saveFields()
        val current = ref
        if (current == null) newTime = null else apply { it.setReminder(current, null) }
        show()
    }

    @Suppress("DEPRECATION")
    private fun pickDate() {
        startActivityForResult(InkDateActivity.intent(this, getString(R.string.due), task?.due ?: newDue.takeIf { task == null }, TaskLists.today()), REQUEST_DATE)
    }

    private fun addSubtask() {
        saveFields()
        val current = ref ?: return run { titleField.requestFocus() }
        startActivity(createSubtask(this, list, current))
    }

    private fun toggleDone() {
        saveFields()
        val current = ref ?: return
        val done = task?.isDone != true
        val wasTop = lists.read(list).tasks.any { it.ref == current }
        apply { it.setDone(current, done, labels.today) }
        if (done && wasTop) close() else show()
    }

    private fun move() {
        saveFields()
        val current = ref ?: return
        startActivity(MainActivity.move(this, current))
        close()
    }

    private fun delete() {
        val current = ref ?: return close()
        lists.change(list) { it.delete(current) }
        close()
    }

    private fun close() {
        isClosed = true
        finish()
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_LIST = "list"
        private const val EXTRA_LINE = "line"
        private const val EXTRA_OCCURRENCE = "occurrence"
        private const val EXTRA_PARENT_LINE = "parent_line"
        private const val EXTRA_PARENT_OCCURRENCE = "parent_occurrence"
        private const val REQUEST_DATE = 1
        private const val REQUEST_TIME = 2
        private const val REQUEST_NOTIFICATIONS = 3
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f
        private const val BUTTON_GAP_DP = 4f
        private const val SECTION_GAP_DP = 24f
        private const val NOTES_MIN_LINES = 3

        fun create(context: Context, list: String): Intent = Intent(context, TaskActivity::class.java).putExtra(EXTRA_LIST, list)

        fun edit(context: Context, list: String, ref: TaskRef): Intent =
            create(context, list).putExtra(EXTRA_LINE, ref.line).putExtra(EXTRA_OCCURRENCE, ref.occurrence)

        fun createSubtask(context: Context, list: String, parent: TaskRef): Intent =
            create(context, list).putExtra(EXTRA_PARENT_LINE, parent.line).putExtra(EXTRA_PARENT_OCCURRENCE, parent.occurrence)

        private fun refOf(intent: Intent, line: String, occurrence: String): TaskRef? =
            intent.getStringExtra(line)?.let { TaskRef(it, intent.getIntExtra(occurrence, 0)) }
    }
}
