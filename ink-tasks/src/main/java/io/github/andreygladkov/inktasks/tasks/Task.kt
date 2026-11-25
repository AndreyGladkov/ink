package io.github.andreygladkov.inktasks.tasks

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A task is found again by the text of its line, the [occurrence]-th line with that text, so a file changed on another
 * device in between still leads to the same task, or to none when its line is gone.
 */
data class TaskRef(val line: String, val occurrence: Int)

data class Task(
    val ref: TaskRef,
    val title: String,
    val isDone: Boolean,
    val due: LocalDate?,
    val doneOn: LocalDate?,
    val reminder: LocalDateTime?,
    val notes: String,
    val subtasks: List<Task>,
)
