package io.github.andreygladkov.inktasks.tasks

import java.time.LocalDate
import java.time.format.DateTimeParseException

internal class TaskLine private constructor(val indent: String, private val bullet: String, private val mark: Char, private val body: String) {
    /**
     * Obsidian draws a box with any mark but a space as checked, and the Tasks plugin's custom statuses (`[/]`, `[-]`)
     * use other marks, so only a space means open.
     */
    val isDone: Boolean get() = mark != ' '

    val due: LocalDate? get() = date(DUE)

    val doneOn: LocalDate? get() = date(DONE)

    val title: String get() = body.replace(DUE, "").replace(DONE, "").replace(SPACES, " ").trim()

    fun withDone(done: Boolean, today: LocalDate): TaskLine = when {
        done == isDone -> this
        done -> TaskLine(indent, bullet, DONE_MARK, "${body.trimEnd()} $DONE_SIGN $today")
        else -> TaskLine(indent, bullet, OPEN_MARK, body.replace(DONE, "").trimEnd())
    }

    /** A new date goes before the done date, where the Tasks plugin writes it. */
    fun withDue(date: LocalDate?): TaskLine {
        val token = date?.let { " $DUE_SIGN $it" }.orEmpty()
        val existing = DUE.find(body)
        val done = DONE.find(body)
        val changed = when {
            existing != null -> body.replaceRange(existing.range, token)
            date == null -> body
            done != null -> body.replaceRange(done.range.first, done.range.first, token)
            else -> body.trimEnd() + token
        }
        return TaskLine(indent, bullet, mark, changed)
    }

    fun withTitle(title: String): TaskLine {
        val dates = (DUE.findAll(body) + DONE.findAll(body)).sortedBy { it.range.first }.joinToString("") { " " + it.value.trim() }
        return TaskLine(indent, bullet, mark, title.trim() + dates)
    }

    override fun toString() = "$indent$bullet [$mark] $body".trimEnd()

    private fun date(pattern: Regex): LocalDate? = pattern.find(body)?.let {
        try {
            LocalDate.parse(it.groupValues[1])
        } catch (_: DateTimeParseException) {
            null
        }
    }

    companion object {
        private const val OPEN_MARK = ' '
        private const val DONE_MARK = 'x'
        private const val DUE_SIGN = "📅"
        private const val DONE_SIGN = "✅"
        private val LINE = Regex("""^(\s*)([-*+]) \[(.)](?: (.*))?$""")
        private val DUE = Regex("""\s*📅️?\s*(\d{4}-\d{2}-\d{2})""")
        private val DONE = Regex("""\s*✅️?\s*(\d{4}-\d{2}-\d{2})""")
        private val SPACES = Regex("""\s+""")

        fun parse(line: String): TaskLine? = LINE.matchEntire(line)?.let {
            val (indent, bullet, mark, body) = it.destructured
            TaskLine(indent, bullet, mark.single(), body)
        }

        fun create(indent: String, title: String, due: LocalDate?) = TaskLine(indent, "-", OPEN_MARK, title.trim()).withDue(due)
    }
}
