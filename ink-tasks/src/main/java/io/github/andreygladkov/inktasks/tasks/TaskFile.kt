package io.github.andreygladkov.inktasks.tasks

import java.time.LocalDate

/**
 * The tasks of one Markdown file. A change rewrites only the lines it is about and keeps every other byte, line
 * endings included, so the file merges cleanly with edits made in Obsidian.
 *
 * A task is a `- [ ]` line without indent. The indented lines right after it belong to it: lines with a box are its
 * subtasks, whatever their depth, and the rest are notes of the nearest task above them that they are indented under.
 */
class TaskFile private constructor(private val bom: String, private val newline: String, private val lines: List<Line>) {
    class Change(val file: TaskFile, val ref: TaskRef?)

    private data class Line(val text: String, val end: String)

    private class Node(val index: Int, val line: TaskLine, val end: Int, val notes: List<Int>, val children: List<Node>)

    private val nodes: List<Node> = parse()
    private val unit: String = indentUnit()

    val tasks: List<Task> by lazy { nodes.map(::task) }

    fun find(ref: TaskRef): Task? = node(ref)?.let(::task)

    fun toText(): String = bom + lines.joinToString("") { it.text + it.end }

    fun add(title: String, due: LocalDate?, notes: String = ""): Change {
        val at = nodes.firstOrNull()?.index ?: lines.size
        return insert(at, listOf(TaskLine.create("", title, due).toString()) + noteLines("", notes)).changed(at)
    }

    fun addSubtask(parent: TaskRef, title: String, due: LocalDate?, notes: String = ""): Change? {
        val node = nodes.firstOrNull { it.index == indexOf(parent) } ?: return null
        val indent = node.children.firstOrNull()?.line?.indent ?: unit
        return insert(node.end, listOf(TaskLine.create(indent, title, due).toString()) + noteLines(indent, notes)).changed(node.end)
    }

    fun setDone(ref: TaskRef, done: Boolean, today: LocalDate): Change? = replace(ref) { it.withDone(done, today) }

    fun setTitle(ref: TaskRef, title: String): Change? = replace(ref) { it.withTitle(title) }

    fun setDue(ref: TaskRef, due: LocalDate?): Change? = replace(ref) { it.withDue(due) }

    fun setNotes(ref: TaskRef, notes: String): Change? {
        val node = node(ref) ?: return null
        val kept = lines.filterIndexed { index, _ -> index !in node.notes }
        val at = node.index + 1
        return TaskFile(bom, newline, kept).insert(at, noteLines(node.line.indent, notes)).changed(node.index)
    }

    fun delete(ref: TaskRef): Change? {
        val node = node(ref) ?: return null
        return Change(remove(node.index, node.end), null)
    }

    fun move(ref: TaskRef, before: TaskRef?): Change? {
        val node = nodes.firstOrNull { it.index == indexOf(ref) } ?: return null
        val target = before?.let { other -> nodes.firstOrNull { it.index == indexOf(other) } ?: return null }
        val others = nodes.filter { it !== node }
        val at = target?.index ?: others.lastOrNull()?.end ?: return Change(this, ref)
        if (target === node) return Change(this, ref)
        val block = lines.subList(node.index, node.end).map { it.text }
        val shifted = if (at > node.index) at - block.size else at
        return remove(node.index, node.end).insert(shifted, block).changed(shifted)
    }

    fun deleteDone(): Change =
        Change(nodes.filter { it.line.isDone }.reversed().fold(this) { file, node -> file.remove(node.index, node.end) }, null)

    private fun replace(ref: TaskRef, change: (TaskLine) -> TaskLine): Change? {
        val node = node(ref) ?: return null
        val line = lines[node.index]
        val replaced = lines.toMutableList().apply { set(node.index, line.copy(text = change(node.line).toString())) }
        return TaskFile(bom, newline, replaced).changed(node.index)
    }

    private fun insert(at: Int, texts: List<String>): TaskFile {
        if (texts.isEmpty()) return this
        val result = lines.toMutableList()
        val endsWithoutNewline = at == lines.size && lines.lastOrNull()?.end == ""
        if (endsWithoutNewline) result[result.lastIndex] = result.last().copy(end = newline)
        val added = texts.mapIndexed { index, text -> Line(text, if (endsWithoutNewline && index == texts.lastIndex) "" else newline) }
        result.addAll(at, added)
        return TaskFile(bom, newline, result)
    }

    private fun remove(from: Int, to: Int): TaskFile = TaskFile(bom, newline, lines.filterIndexed { index, _ -> index !in from until to })

    private fun changed(index: Int) = Change(this, refAt(index))

    private fun noteLines(indent: String, notes: String): List<String> =
        notes.trimEnd().takeIf { it.isNotEmpty() }?.lines()?.map { indent + unit + it.trimEnd() }.orEmpty()

    private fun task(node: Node): Task = Task(
        ref = refAt(node.index),
        title = node.line.title,
        isDone = node.line.isDone,
        due = node.line.due,
        doneOn = node.line.doneOn,
        notes = node.notes.joinToString("\n") { dropIndent(lines[it].text, columns(node.line.indent) + columns(unit)) }.trimEnd(),
        subtasks = node.children.map(::task),
    )

    private fun refAt(index: Int): TaskRef {
        val text = lines[index].text
        return TaskRef(text, (0 until index).count { lines[it].text == text })
    }

    private fun indexOf(ref: TaskRef): Int? {
        var seen = 0
        lines.forEachIndexed { index, line ->
            if (line.text == ref.line && seen++ == ref.occurrence) return index
        }
        return null
    }

    private fun node(ref: TaskRef): Node? {
        val index = indexOf(ref) ?: return null
        return nodes.asSequence().flatMap { sequenceOf(it) + it.children }.firstOrNull { it.index == index }
    }

    private fun parse(): List<Node> {
        val hidden = hiddenLines()
        val result = mutableListOf<Node>()
        var index = 0
        while (index < lines.size) {
            val line = TaskLine.parse(lines[index].text)
            if (index in hidden || line == null || line.indent.isNotEmpty()) {
                index++
                continue
            }
            var end = index + 1
            while (end < lines.size && isContinuation(lines[end].text)) end++
            result += block(index, line, end)
            index = end
        }
        return result
    }

    private class Sub(val index: Int, val line: TaskLine, val columns: Int, val notes: MutableList<Int> = mutableListOf())

    private fun block(index: Int, line: TaskLine, end: Int): Node {
        val notes = mutableListOf<Int>()
        val subs = mutableListOf<Sub>()
        for (inner in index + 1 until end) {
            val text = lines[inner].text
            val sub = TaskLine.parse(text)
            val current = subs.lastOrNull()
            when {
                sub != null -> subs += Sub(inner, sub, columns(sub.indent))
                current != null && columns(leading(text)) > current.columns -> current.notes += inner
                else -> notes += inner
            }
        }
        val children = subs.map { sub ->
            var subEnd = sub.index + 1
            while (subEnd < end && columns(leading(lines[subEnd].text)) > sub.columns) subEnd++
            Node(sub.index, sub.line, subEnd, sub.notes, emptyList())
        }
        return Node(index, line, end, notes, children)
    }

    /** Boxes in frontmatter and in fenced code are text, not tasks, as in Obsidian. */
    private fun hiddenLines(): Set<Int> {
        val hidden = mutableSetOf<Int>()
        if (lines.firstOrNull()?.text == FRONTMATTER) {
            val close = (1 until lines.size).firstOrNull { lines[it].text == FRONTMATTER || lines[it].text == FRONTMATTER_END }
            if (close != null) hidden += 0..close
        }
        var fence: String? = null
        lines.forEachIndexed { index, line ->
            val trimmed = line.text.trimStart()
            val marker = FENCES.firstOrNull { trimmed.startsWith(it) }
            if (fence != null || marker != null) hidden += index
            when {
                fence == null && marker != null -> fence = marker
                fence != null && marker == fence -> fence = null
            }
        }
        return hidden
    }

    /** New lines take the file's own indent when it indents with spaces; Obsidian's default is a tab. */
    private fun indentUnit(): String {
        val indented = nodes.asSequence().flatMap { node -> (node.index + 1 until node.end).asSequence() }.map { leading(lines[it].text) }
        return indented.firstOrNull { it.isNotEmpty() }?.takeIf { it.all { char -> char == ' ' } } ?: TAB
    }

    companion object {
        private const val TAB = "\t"
        private const val TAB_COLUMNS = 4
        private const val BOM = "\uFEFF"
        private const val FRONTMATTER = "---"
        private const val FRONTMATTER_END = "..."
        private val FENCES = listOf("```", "~~~")

        fun parse(text: String): TaskFile {
            val bom = if (text.startsWith(BOM)) BOM else ""
            val body = text.removePrefix(bom)
            val newline = if ("\r\n" in body) "\r\n" else "\n"
            val lines = mutableListOf<Line>()
            var start = 0
            while (start < body.length) {
                val feed = body.indexOf('\n', start)
                if (feed < 0) {
                    lines += Line(body.substring(start), "")
                    break
                }
                val crlf = feed > start && body[feed - 1] == '\r'
                lines += Line(body.substring(start, if (crlf) feed - 1 else feed), if (crlf) "\r\n" else "\n")
                start = feed + 1
            }
            return TaskFile(bom, newline, lines)
        }

        private fun isContinuation(text: String) = text.isNotEmpty() && (text[0] == ' ' || text[0] == '\t')

        private fun leading(text: String) = text.takeWhile { it == ' ' || it == '\t' }

        private fun columns(indent: String) = indent.fold(0, ::advance)

        private fun advance(column: Int, char: Char) = if (char == '\t') (column / TAB_COLUMNS + 1) * TAB_COLUMNS else column + 1

        private fun dropIndent(text: String, upTo: Int): String {
            var column = 0
            var index = 0
            while (index < text.length && column < upTo && (text[index] == ' ' || text[index] == '\t')) {
                column = advance(column, text[index])
                index++
            }
            return text.substring(index)
        }
    }
}
