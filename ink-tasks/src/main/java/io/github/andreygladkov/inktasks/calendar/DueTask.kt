package io.github.andreygladkov.inktasks.calendar

import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.tasks.TaskFile
import io.github.andreygladkov.inktasks.tasks.TaskRef
import java.time.LocalDate
import java.time.LocalDateTime

data class DueTask(val list: String, val ref: TaskRef, val title: String, val due: LocalDate, val reminder: LocalDateTime?) {
    companion object {
        fun inFile(list: String, file: TaskFile): List<DueTask> =
            file.open.mapNotNull { task -> task.due?.let { DueTask(list, task.ref, task.title, it, task.reminder) } }

        fun all(lists: TaskLists): List<DueTask> = lists.names().flatMap { inFile(it, lists.read(it)) }
    }
}
