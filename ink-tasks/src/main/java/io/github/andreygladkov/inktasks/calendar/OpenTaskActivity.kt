package io.github.andreygladkov.inktasks.calendar

import android.app.Activity
import android.os.Bundle
import io.github.andreygladkov.inktasks.task.TaskActivity
import io.github.andreygladkov.inktasks.tasks.TaskRef

/** Opens a task for Ink Calendar in the calendar's own task, so Back returns to the calendar. */
class OpenTaskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = intent.getStringExtra(EXTRA_LIST)
        val line = intent.getStringExtra(EXTRA_LINE)
        if (list != null && line != null) startActivity(TaskActivity.edit(this, list, TaskRef(line, intent.getIntExtra(EXTRA_OCCURRENCE, 0))))
        finish()
    }

    private companion object {
        const val EXTRA_LIST = "list"
        const val EXTRA_LINE = "line"
        const val EXTRA_OCCURRENCE = "occurrence"
    }
}
