package io.github.andreygladkov.inktasks.store

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inktasks.Fixtures
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.github.andreygladkov.ink.A9_QUALIFIERS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class TaskListsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val lists = TaskLists.of(context)

    @Test fun withoutFilesTheCurrentListIsTheDefaultOne() {
        assertEquals(emptyList(), lists.names())
        assertEquals("Мои задачи", lists.current)
    }

    @Test fun theDefaultListGetsItsFileWithTheFirstTask() {
        lists.change(lists.current) { it.add("Первая", null) }
        assertEquals("- [ ] Первая\n", Fixtures.read(context, "Мои задачи"))
        assertEquals(listOf("Мои задачи"), lists.names())
    }

    @Test fun listsGoInAlphabeticalOrderAndOnlyMarkdownFilesCount() {
        listOf("работа", "Дом", "Альфа").forEach { Fixtures.write(context, it, "") }
        Fixtures.file(context, "x").resolveSibling("notes.txt").writeText("")
        assertEquals(listOf("Альфа", "Дом", "работа"), lists.names())
        assertEquals("Альфа", lists.current)
        lists.open("Дом")
        assertEquals("Дом", lists.current)
    }

    @Test fun aNameIsTakenWhateverItsCase() {
        assertTrue(lists.create("Дом"))
        assertFalse(lists.create("дом"))
        assertTrue(lists.create("Работа"))
        assertFalse(lists.rename("Работа", "ДОМ"))
        assertTrue(lists.rename("Дом", "ДОМ"))
        assertEquals(listOf("ДОМ", "Работа"), lists.names())
    }

    @Test fun renamingKeepsTheSortAndTheCurrentList() {
        Fixtures.write(context, "Дом", "- [ ] a\n")
        lists.open("Дом")
        lists.setSort("Дом", TaskLists.Sort.DUE)
        assertTrue(lists.rename("Дом", "Дача"))
        assertEquals("Дача", lists.current)
        assertEquals(TaskLists.Sort.DUE, lists.sort("Дача"))
        assertEquals(TaskLists.Sort.MANUAL, lists.sort("Дом"))
        assertEquals("- [ ] a\n", Fixtures.read(context, "Дача"))
    }

    @Test fun deletingRemovesTheFile() {
        Fixtures.write(context, "Дом", "- [ ] a\n")
        lists.delete("Дом")
        assertFalse(Fixtures.file(context, "Дом").exists())
        assertEquals(emptyList(), lists.names())
    }

    @Test fun aChangeReadsTheFileAnewAndWritesOnlyWhenItApplies() {
        Fixtures.write(context, "Дом", "- [ ] a\n")
        val ref = lists.read("Дом").tasks.single().ref
        Fixtures.write(context, "Дом", "- [ ] from the computer\n- [ ] a\n")
        lists.change("Дом") { it.setDone(ref, true, Fixtures.TODAY) }
        assertEquals("- [ ] from the computer\n- [x] a ✅ 2026-11-24\n", Fixtures.read(context, "Дом"))
        Fixtures.write(context, "Дом", "- [ ] b\n")
        assertEquals(null, lists.change("Дом") { it.delete(ref) })
        assertEquals("- [ ] b\n", Fixtures.read(context, "Дом"))
    }
}
