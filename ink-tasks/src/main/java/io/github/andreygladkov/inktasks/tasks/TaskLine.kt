package io.github.andreygladkov.inktasks.tasks

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal class TaskLine private constructor(val indent: String, private val bullet: String, private val mark: Char, private val body: String) {
    /**
     * Obsidian draws a box with any mark but a space as checked, and the Tasks plugin's custom statuses (`[/]`, `[-]`)
     * use other marks, so only a space means open.
     */
    val isDone: Boolean get() = mark != ' '

    val due: LocalDate? get() = date(DUE)

    val doneOn: LocalDate? get() = date(DONE)

    /** Obsidian Reminder writes a date without a time for its default time of day, which is 09:00 unless changed there. */
    val reminder: LocalDateTime?
        get() = REMINDER.find(body)?.let { match ->
            val date = date(match) ?: return null
            val time = match.groupValues[2].takeIf { it.isNotEmpty() }?.let { parseTime(it) ?: return null } ?: DEFAULT_REMINDER_TIME
            date.atTime(time)
        }

    val title: String get() = body.replace(DUE, "").replace(DONE, "").replace(REMINDER, "").replace(SPACES, " ").trim()

    fun withDone(done: Boolean, today: LocalDate): TaskLine = when {
        done == isDone -> this
        done -> TaskLine(indent, bullet, DONE_MARK, "${body.trimEnd()} $DONE_SIGN $today")
        else -> TaskLine(indent, bullet, OPEN_MARK, body.replace(DONE, "").trimEnd())
    }

    fun withDue(date: LocalDate?): TaskLine {
        val moving = reminder?.takeIf { it.toLocalDate() == due }
        val changed = withDueOnly(date)
        return if (moving == null) changed else changed.withReminder(date?.atTime(moving.toLocalTime()))
    }

    /**
     * Obsidian Reminder finds `⏰` only right after the description, before the Tasks plugin's fields, so a new one goes
     * before the first of them.
     */
    fun withReminder(at: LocalDateTime?): TaskLine {
        val token = at?.let { " $REMINDER_SIGN ${it.toLocalDate()} ${it.format(TIME)}" }.orEmpty()
        val existing = REMINDER.find(body)
        val changed = when {
            existing != null -> body.replaceRange(existing.range, token)
            at == null -> body
            else -> FIELD.find(body)?.let { body.replaceRange(it.range.first, it.range.first, token) } ?: (body.trimEnd() + token)
        }
        return TaskLine(indent, bullet, mark, changed)
    }

    /** A new date goes before the done date, where the Tasks plugin writes it. */
    private fun withDueOnly(date: LocalDate?): TaskLine {
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
        val dates = (REMINDER.findAll(body) + DUE.findAll(body) + DONE.findAll(body)).sortedBy { it.range.first }.joinToString("") { " " + it.value.trim() }
        return TaskLine(indent, bullet, mark, title.trim() + dates)
    }

    override fun toString() = "$indent$bullet [$mark] $body".trimEnd()

    private fun date(pattern: Regex): LocalDate? = pattern.find(body)?.let { date(it) }

    private fun date(match: MatchResult): LocalDate? = try {
        LocalDate.parse(match.groupValues[1])
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseTime(text: String): LocalTime? = try {
        LocalTime.parse(text.padStart(TIME_LENGTH, '0'))
    } catch (_: DateTimeParseException) {
        null
    }

    companion object {
        private const val OPEN_MARK = ' '
        private const val DONE_MARK = 'x'
        private const val DUE_SIGN = "📅"
        private const val DONE_SIGN = "✅"
        private const val REMINDER_SIGN = "⏰"
        private const val TIME_LENGTH = 5
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
        private val DEFAULT_REMINDER_TIME = LocalTime.of(9, 0)
        private val LINE = Regex("""^(\s*)([-*+]) \[(.)](?: (.*))?$""")
        private val DUE = Regex("""\s*📅️?\s*(\d{4}-\d{2}-\d{2})""")
        private val DONE = Regex("""\s*✅️?\s*(\d{4}-\d{2}-\d{2})""")
        private val REMINDER = Regex("""\s*⏰️?\s*(\d{4}-\d{2}-\d{2})(?:\s+(\d{1,2}:\d{2}))?""")
        private val FIELD = Regex("""\s*(?:📅|✅|⏳|🛫|➕|❌|🔁|⏫|🔼|🔽|⏬|🔺|🆔|⛔)""")
        private val SPACES = Regex("""\s+""")

        fun parse(line: String): TaskLine? = LINE.matchEntire(line)?.let {
            val (indent, bullet, mark, body) = it.destructured
            TaskLine(indent, bullet, mark.single(), body)
        }

        fun create(indent: String, title: String, due: LocalDate?, reminder: LocalDateTime? = null) =
            TaskLine(indent, "-", OPEN_MARK, title.trim()).withDue(due).withReminder(reminder)
    }
}
