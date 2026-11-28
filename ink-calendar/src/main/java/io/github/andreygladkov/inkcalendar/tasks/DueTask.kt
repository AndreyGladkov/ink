package io.github.andreygladkov.inkcalendar.tasks

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class DueTask(val list: String, val line: String, val occurrence: Int, val title: String, val due: LocalDate, val reminder: LocalDateTime?) {
    /** A reminder on another day than the due date says nothing about the time on the due date. */
    val time: LocalTime? get() = reminder?.takeIf { it.toLocalDate() == due }?.toLocalTime()

    companion object {
        fun onDay(tasks: List<DueTask>, date: LocalDate): List<DueTask> =
            tasks.filter { it.due == date }.sortedWith(compareBy(nullsLast()) { it: DueTask -> it.time })
    }
}
