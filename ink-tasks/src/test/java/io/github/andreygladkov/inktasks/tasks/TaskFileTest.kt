package io.github.andreygladkov.inktasks.tasks

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskFileTest {
    private val today = LocalDate.of(2026, 11, 24)

    private val sample = """
        ---
        tags: [home]
        - [ ] not a task in frontmatter
        ---
        # Дом

        Просто абзац.

        - [ ] Купить молоко 📅 2026-11-25
        	2,5%, не ультрапастеризованное
        	- [ ] Заодно хлеб
        		без семечек
        - [x] Позвонить маме ✅ 2026-11-24
        - Обычный пункт

        ```
        - [ ] в коде
        ```
        - [ ] Починить кран #дом ⏫
    """.trimIndent() + "\n"

    private val file = TaskFile.parse(sample)

    @Test fun anUntouchedFileIsWrittenBackByteForByte() {
        listOf(sample, sample.replace("\n", "\r\n"), "\uFEFF" + sample, sample.trimEnd('\n'), "", "\n\n", "- [ ] one").forEach {
            assertEquals(it, TaskFile.parse(it).toText())
        }
    }

    @Test fun tasksAreTheBoxesWithoutIndentOutsideFrontmatterAndCode() {
        assertEquals(listOf("Купить молоко", "Позвонить маме", "Починить кран #дом ⏫"), file.tasks.map { it.title })
    }

    @Test fun aTaskHasItsDatesNotesAndSubtasks() {
        val milk = file.tasks[0]
        assertEquals(LocalDate.of(2026, 11, 25), milk.due)
        assertEquals(false, milk.isDone)
        assertEquals("2,5%, не ультрапастеризованное", milk.notes)
        assertEquals(listOf("Заодно хлеб"), milk.subtasks.map { it.title })
        assertEquals("без семечек", milk.subtasks[0].notes)
        val mum = file.tasks[1]
        assertTrue(mum.isDone)
        assertEquals(today, mum.doneOn)
        assertNull(mum.due)
    }

    @Test fun anyMarkButASpaceIsDone() {
        val tasks = TaskFile.parse("- [/] in progress\n- [-] cancelled\n* [X] star\n+ [ ] plus\n").tasks
        assertEquals(listOf(true, true, true, false), tasks.map { it.isDone })
    }

    @Test fun deeperSubtasksAreShownUnderTheTopTask() {
        val tasks = TaskFile.parse("- [ ] a\n\t- [ ] b\n\t\t- [ ] c\n\tnote of a\n- [ ] d\n").tasks
        assertEquals(listOf("b", "c"), tasks[0].subtasks.map { it.title })
        assertEquals("note of a", tasks[0].notes)
        assertEquals("d", tasks[1].title)
    }

    @Test fun anEmptyLineEndsATasksBlock() {
        val tasks = TaskFile.parse("- [ ] a\n\n\tcode block\n").tasks
        assertEquals("", tasks.single().notes)
    }

    @Test fun completingChangesOnlyThatLine() {
        val changed = file.setDone(file.tasks[0].ref, true, today)!!
        assertEquals(sample.replace("- [ ] Купить молоко 📅 2026-11-25", "- [x] Купить молоко 📅 2026-11-25 ✅ 2026-11-24"), changed.file.toText())
        val reopened = changed.file.setDone(changed.ref!!, false, today)!!
        assertEquals(sample, reopened.file.toText())
    }

    @Test fun aSubtaskIsCompletedInPlace() {
        val bread = file.tasks[0].subtasks[0]
        val changed = file.setDone(bread.ref, true, today)!!
        assertTrue(changed.file.tasks[0].subtasks[0].isDone)
        assertEquals(sample.replace("\t- [ ] Заодно хлеб", "\t- [x] Заодно хлеб ✅ 2026-11-24"), changed.file.toText())
    }

    @Test fun aNewTaskGoesBeforeTheFirstTask() {
        val changed = file.add("Вынести мусор", LocalDate.of(2026, 11, 26))
        assertEquals(sample.replace("- [ ] Купить молоко", "- [ ] Вынести мусор 📅 2026-11-26\n- [ ] Купить молоко"), changed.file.toText())
        assertEquals("Вынести мусор", changed.file.find(changed.ref!!)!!.title)
    }

    @Test fun aNewTaskInAFileWithoutTasksGoesToTheEnd() {
        assertEquals("# Notes\n- [ ] one\n", TaskFile.parse("# Notes\n").add("one", null).file.toText())
        assertEquals("# Notes\n- [ ] one", TaskFile.parse("# Notes").add("one", null).file.toText())
        assertEquals("- [ ] one\n", TaskFile.parse("").add("one", null).file.toText())
    }

    @Test fun aNewTaskKeepsTheFilesLineEndings() {
        val text = "# Notes\r\n\r\n- [ ] old\r\n"
        val added = TaskFile.parse(text).add("new", null, notes = "line 1\nline 2").file.toText()
        assertEquals("# Notes\r\n\r\n- [ ] new\r\n\tline 1\r\n\tline 2\r\n- [ ] old\r\n", added)
    }

    @Test fun aSubtaskGoesToTheEndOfItsTasksBlock() {
        val changed = file.addSubtask(file.tasks[0].ref, "Сыр", null)!!
        assertEquals(sample.replace("\t\tбез семечек\n", "\t\tбез семечек\n\t- [ ] Сыр\n"), changed.file.toText())
        assertEquals(listOf("Заодно хлеб", "Сыр"), changed.file.tasks[0].subtasks.map { it.title })
    }

    @Test fun newLinesTakeTheFilesSpaceIndent() {
        val text = "- [ ] a\n    - [ ] b\n"
        val changed = TaskFile.parse(text).addSubtask(TaskFile.parse(text).tasks[0].ref, "c", null, notes = "n")!!
        assertEquals("- [ ] a\n    - [ ] b\n    - [ ] c\n        n\n", changed.file.toText())
        assertEquals("n", changed.file.tasks[0].subtasks[1].notes)
    }

    @Test fun theTitleChangesAndTheDatesStay() {
        val changed = file.setTitle(file.tasks[1].ref, "  Позвонить папе ")!!
        assertEquals(sample.replace("Позвонить маме ✅", "Позвонить папе ✅"), changed.file.toText())
        val tagged = file.setTitle(file.tasks[2].ref, "Починить кран")!!
        assertEquals(sample.replace("Починить кран #дом ⏫", "Починить кран"), tagged.file.toText())
    }

    @Test fun theDueDateIsReplacedAddedBeforeTheDoneDateOrRemoved() {
        val moved = file.setDue(file.tasks[0].ref, LocalDate.of(2026, 12, 1))!!
        assertEquals(sample.replace("📅 2026-11-25", "📅 2026-12-01"), moved.file.toText())
        val added = file.setDue(file.tasks[1].ref, LocalDate.of(2026, 11, 20))!!
        assertEquals(sample.replace("Позвонить маме ✅", "Позвонить маме 📅 2026-11-20 ✅"), added.file.toText())
        val removed = file.setDue(file.tasks[0].ref, null)!!
        assertEquals(sample.replace("Купить молоко 📅 2026-11-25", "Купить молоко"), removed.file.toText())
    }

    @Test fun notesAreRewrittenRightUnderTheirTask() {
        val changed = file.setNotes(file.tasks[0].ref, "первая\n\nтретья\n\n")!!
        assertEquals(sample.replace("\t2,5%, не ультрапастеризованное\n", "\tпервая\n\t\n\tтретья\n"), changed.file.toText())
        assertEquals("первая\n\nтретья", changed.file.tasks[0].notes)
        assertEquals(listOf("Заодно хлеб"), changed.file.tasks[0].subtasks.map { it.title })
        val cleared = file.setNotes(file.tasks[0].ref, "")!!
        assertEquals(sample.replace("\t2,5%, не ультрапастеризованное\n", ""), cleared.file.toText())
    }

    @Test fun deletingATaskTakesItsNotesAndSubtasks() {
        val changed = file.delete(file.tasks[0].ref)!!
        assertEquals(sample.replace("- [ ] Купить молоко 📅 2026-11-25\n\t2,5%, не ультрапастеризованное\n\t- [ ] Заодно хлеб\n\t\tбез семечек\n", ""), changed.file.toText())
        val sub = file.delete(file.tasks[0].subtasks[0].ref)!!
        assertEquals(sample.replace("\t- [ ] Заодно хлеб\n\t\tбез семечек\n", ""), sub.file.toText())
    }

    @Test fun aTaskMovesWithItsBlock() {
        val text = "- [ ] a\n\tnote\n- [ ] b\n- [ ] c\n"
        val tasks = TaskFile.parse(text).tasks
        assertEquals("- [ ] b\n- [ ] a\n\tnote\n- [ ] c\n", TaskFile.parse(text).move(tasks[0].ref, tasks[2].ref)!!.file.toText())
        assertEquals("- [ ] b\n- [ ] c\n- [ ] a\n\tnote\n", TaskFile.parse(text).move(tasks[0].ref, null)!!.file.toText())
        val up = TaskFile.parse(text).move(tasks[2].ref, tasks[0].ref)!!
        assertEquals("- [ ] c\n- [ ] a\n\tnote\n- [ ] b\n", up.file.toText())
        assertEquals("c", up.file.find(up.ref!!)!!.title)
    }

    @Test fun deletingDoneTasksKeepsTheRest() {
        assertEquals(sample.replace("- [x] Позвонить маме ✅ 2026-11-24\n", ""), file.deleteDone().file.toText())
    }

    @Test fun aTaskIsFoundByTheTextOfItsLineAndItsOccurrence() {
        val text = "- [ ] same\n- [ ] other\n- [ ] same\n"
        val second = TaskFile.parse(text).tasks[2].ref
        assertEquals(TaskRef("- [ ] same", 1), second)
        val changed = TaskFile.parse(text).setDone(second, true, today)!!
        assertEquals("- [ ] same\n- [ ] other\n- [x] same ✅ 2026-11-24\n", changed.file.toText())
    }

    @Test fun aChangeToAGoneTaskDoesNothing() {
        val gone = TaskRef("- [ ] gone", 0)
        assertNull(file.setDone(gone, true, today))
        assertNull(file.delete(gone))
        assertNull(file.setNotes(gone, "x"))
        assertNull(file.move(gone, null))
        assertNull(file.addSubtask(gone, "x", null))
    }
}
