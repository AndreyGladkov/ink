package io.github.andreygladkov.inktasks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.BackToStart
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inktasks.lists.ListsActivity
import io.github.andreygladkov.inktasks.remind.Reminders
import io.github.andreygladkov.inktasks.settings.FolderPicker
import io.github.andreygladkov.inktasks.settings.SettingsActivity
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.task.TaskActivity
import io.github.andreygladkov.inktasks.tasks.Task
import io.github.andreygladkov.inktasks.tasks.TaskRef
import io.github.andreygladkov.inktasks.ui.DueLabels
import io.github.andreygladkov.inktasks.ui.ListDots
import io.github.andreygladkov.inktasks.ui.TaskRows
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private sealed interface Row
    private data class TaskRow(val task: Task, val isSubtask: Boolean, val isMoving: Boolean = false) : Row
    private data class DoneRow(val count: Int) : Row
    private data class Slot(val before: TaskRef?) : Row

    private val lists get() = TaskLists.of(this)
    private lateinit var style: InkStyle
    private lateinit var title: InkButton
    private lateinit var add: View
    private lateinit var settings: View
    private lateinit var list: InkPagedList
    private lateinit var prevList: InkButton
    private lateinit var nextList: InkButton
    private lateinit var dots: ListDots
    private lateinit var empty: InkTextView
    private lateinit var pickFolder: InkButton
    private lateinit var back: BackToStart
    private var rows = emptyList<Row>()
    private var moving: TaskRef? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        style = InkStyle.of(this)
        back = BackToStart(this) {
            moving = null
            show()
        }
        takeMove(intent)
        takeList(intent)
        title = InkHeader.action(this, R.drawable.ic_list, "") { if (moving == null && lists.isAvailable) startActivity(ListsActivity.intent(this)) }.apply {
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        }
        add = InkHeader.button(this, R.drawable.ic_add, getString(R.string.new_task)) { startActivity(TaskActivity.create(this, lists.current)) }
        settings = InkHeader.settings(this) { startActivity(SettingsActivity.intent(this)) }
        list = InkPagedList(this)
        prevList = listButton(R.drawable.ic_chevron_left, R.string.prev_list, -1)
        nextList = listButton(R.drawable.ic_chevron_right, R.string.next_list, 1)
        list.addFooterView(prevList, atStart = true)
        list.addFooterView(nextList)
        dots = ListDots(this)
        list.addFooterCenterView(dots)
        empty = InkTextView(this).apply { gravity = Gravity.CENTER }
        pickFolder = InkButton(this).apply {
            setText(R.string.choose_folder)
            setOnClickListener { FolderPicker.open(this@MainActivity) }
        }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@MainActivity, title, null, add, settings))
            addView(FrameLayout(this@MainActivity).apply {
                addView(list)
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(empty)
                    addView(pickFolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(PADDING_DP)
                    })
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeMove(intent)
        takeList(intent)
    }

    override fun onResume() {
        super.onResume()
        if (InkStyle.of(this) != style) return recreate()
        if (Storage(this).chosen != null) Reminders.update(this)
        show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (FolderPicker.chosen(this, requestCode, resultCode, data) != null) show()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back.onBackPressed()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** The extra is dropped once read, so a screen recreated later does not start moving again. */
    private fun takeMove(intent: Intent) {
        val line = intent.getStringExtra(EXTRA_MOVE_LINE) ?: return
        moving = TaskRef(line, intent.getIntExtra(EXTRA_MOVE_OCCURRENCE, 0))
        intent.removeExtra(EXTRA_MOVE_LINE)
    }

    private fun takeList(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_LIST) ?: return
        lists.open(name)
        intent.removeExtra(EXTRA_LIST)
    }

    private fun show(position: Int = list.firstPosition) {
        val lists = lists
        val available = lists.isAvailable
        pickFolder.visibility = if (available) View.GONE else View.VISIBLE
        empty.setText(if (available) R.string.no_tasks else R.string.storage_unavailable)
        if (!available) moving = null
        val names = lists.names()
        val name = lists.current(names)
        val tasks = lists.read(name).tasks
        val open = tasks.filter { !it.isDone }
        if (open.none { it.ref == moving }) moving = null
        val moving = moving
        back.isAway = moving != null
        title.text = if (moving != null) getString(R.string.move_where) else name
        title.icon = if (moving != null) 0 else R.drawable.ic_list
        add.visibility = if (moving != null || !available) View.GONE else View.VISIBLE
        settings.visibility = if (moving != null) View.GONE else View.VISIBLE
        val switches = if (moving == null && available && names.size > 1) View.VISIBLE else View.GONE
        prevList.visibility = switches
        nextList.visibility = switches
        dots.visibility = switches
        dots.show(names.indexOf(name), names.size)
        val done = tasks.count { it.isDone }
        rows = when {
            moving != null -> moveRows(open, moving)
            lists.sort(name) == TaskLists.Sort.DUE -> taskRows(open.sortedWith(compareBy(nullsLast()) { it.due })) + doneRow(done)
            else -> taskRows(open) + doneRow(done)
        }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val shown = rows
        val taskRows = TaskRows(this, DueLabels(this, TaskLists.today()), ::toggle, ::open)
        list.setAdapter(object : InkPagedList.Adapter {
            override val count get() = shown.size
            override fun itemId(position: Int) = shown[position].hashCode().toLong()
            override fun createView(position: Int) = view(shown[position], taskRows)
        }, position.coerceAtMost(shown.size - 1).coerceAtLeast(0))
    }

    private fun listButton(icon: Int, description: Int, step: Int) = InkButton(this).apply {
        this.icon = icon
        contentDescription = getString(description)
        isBordered = false
        setOnClickListener { switchList(step) }
    }

    private fun switchList(step: Int) {
        val lists = lists
        val names = lists.names()
        val at = names.indexOf(lists.current(names))
        if (moving != null || names.size < 2 || at < 0) return
        lists.open(names[(at + step).mod(names.size)])
        show(position = 0)
        A9Panel.onPageFlipped(this)
    }

    private fun taskRows(tasks: List<Task>): List<Row> = tasks.flatMap { task -> listOf(TaskRow(task, false)) + task.subtasks.map { TaskRow(it, true) } }

    private fun doneRow(count: Int): List<Row> = if (count > 0) listOf(DoneRow(count)) else emptyList()

    /** No mark next to the task itself: dropping it there would leave it where it is. */
    private fun moveRows(open: List<Task>, moving: TaskRef): List<Row> {
        val from = open.indexOfFirst { it.ref == moving }
        return buildList {
            open.forEachIndexed { index, task ->
                if (index != from && index != from + 1) add(Slot(task.ref))
                add(TaskRow(task, false, isMoving = index == from))
                task.subtasks.forEach { add(TaskRow(it, true)) }
            }
            if (from != open.lastIndex) add(Slot(null))
        }
    }

    private fun view(row: Row, taskRows: TaskRows): View = when (row) {
        is TaskRow -> taskRows.row(row.task, row.isSubtask, row.isMoving)
        is DoneRow -> wideButton(getString(R.string.done_count, row.count)) { startActivity(DoneActivity.intent(this, lists.current)) }
        is Slot -> wideButton(getString(R.string.move_here), InkStyle.SECONDARY_SCALE) { moveBefore(row.before) }
    }

    private fun wideButton(label: String, scale: Float = 1f, onClick: () -> Unit) = LinearLayout(this).apply {
        val padding = dp(PADDING_DP)
        setPadding(padding, dp(InkPagedList.ROW_PADDING_DP), padding, dp(InkPagedList.ROW_PADDING_DP))
        addView(InkButton(this@MainActivity).apply {
            text = label
            textScale = scale
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun toggle(task: Task) {
        if (moving != null) return
        lists.change(lists.current) { it.setDone(task.ref, !task.isDone, TaskLists.today()) }
        show()
    }

    private fun open(task: Task) {
        if (moving != null) return
        startActivity(TaskActivity.edit(this, lists.current, task.ref))
    }

    private fun moveBefore(before: TaskRef?) {
        val ref = moving ?: return
        lists.change(lists.current) { it.move(ref, before) }
        moving = null
        show()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_MOVE_LINE = "move_line"
        private const val EXTRA_MOVE_OCCURRENCE = "move_occurrence"
        private const val EXTRA_LIST = "list"
        private const val PADDING_DP = 16f

        fun move(context: Context, ref: TaskRef): Intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_MOVE_LINE, ref.line)
            .putExtra(EXTRA_MOVE_OCCURRENCE, ref.occurrence)

        fun open(context: Context, list: String): Intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_LIST, list)
    }
}
