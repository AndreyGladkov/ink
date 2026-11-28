package io.github.andreygladkov.inktasks

import android.content.Context
import android.content.Intent
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.confirmBeforeAction
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.task.TaskActivity
import io.github.andreygladkov.inktasks.ui.DueLabels
import io.github.andreygladkov.inktasks.ui.TaskRows

class DoneActivity : InkPagedActivity() {
    private val lists get() = TaskLists.of(this)
    private val list get() = intent.getStringExtra(EXTRA_LIST).orEmpty()

    override fun headerTitle(): String = getString(R.string.done)

    override fun outsideState(): Any = lists.read(list).toText()

    override fun InkPagedItems.build() {
        val activity = this@DoneActivity
        val labels = DueLabels(activity, TaskLists.today())
        val done = lists.read(list).tasks.filter { it.isDone }
        if (done.isEmpty()) return item { InkTextView(activity).apply { setText(R.string.no_done) } }
        val rows = TaskRows(activity, labels, onCheck = { task ->
            lists.change(list) { it.setDone(task.ref, false, labels.today) }
            rebuild()
        }, onOpen = { task -> startActivity(TaskActivity.edit(activity, list, task.ref)) })
        done.forEach { task -> item { rows.row(task, detail = task.doneOn?.let { getString(R.string.done_on, labels.dayInSentence(it)) }) } }
        item {
            InkSettings.rowButton(activity, getString(R.string.delete_done)) {}.apply {
                confirmBeforeAction(R.string.delete_done_confirm) {
                    lists.change(list) { it.deleteDone() }
                    rebuild()
                }
            }
        }
    }

    companion object {
        private const val EXTRA_LIST = "list"

        fun intent(context: Context, list: String): Intent = Intent(context, DoneActivity::class.java).putExtra(EXTRA_LIST, list)
    }
}
