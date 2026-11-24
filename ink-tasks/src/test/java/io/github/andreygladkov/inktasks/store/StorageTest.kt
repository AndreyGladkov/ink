package io.github.andreygladkov.inktasks.store

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inktasks.Fixtures
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class StorageTest {
    @get:Rule val temp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val storage = Storage(context)
    private lateinit var tree: Uri
    private lateinit var vault: File

    @Before fun installProvider() {
        tree = TestDocumentsProvider.install(temp.root, "Tasks")
        vault = File(temp.root, "Tasks")
    }

    @Test fun aFolderIsReadAndWrittenThroughTheProvider() {
        File(vault, "Дом.md").writeText("- [ ] a\n")
        File(vault, "notes.txt").writeText("")
        File(vault, "Sub").mkdir()
        assertTrue(storage.choose(tree))
        val lists = TaskLists.of(context)
        assertTrue(lists.isAvailable)
        assertEquals(listOf("Дом"), lists.names())
        lists.change("Дом") { it.add("b", null) }
        assertEquals("- [ ] b\n- [ ] a\n", File(vault, "Дом.md").readText())
        lists.change("Дом") { it.delete(it.tasks[0].ref) }
        assertEquals("- [ ] a\n", File(vault, "Дом.md").readText())
    }

    @Test fun listsAreCreatedRenamedAndDeletedAsFiles() {
        storage.choose(tree)
        val lists = TaskLists.of(context)
        assertTrue(lists.create("Работа"))
        assertTrue(File(vault, "Работа.md").exists())
        lists.change("Мои задачи") { it.add("first", null) }
        assertEquals("- [ ] first\n", File(vault, "Мои задачи.md").readText())
        assertTrue(lists.rename("Работа", "Офис"))
        assertEquals(listOf("Мои задачи.md", "Офис.md"), vault.list()!!.sorted())
        lists.delete("Офис")
        assertFalse(File(vault, "Офис.md").exists())
    }

    @Test fun listsOnThePhoneMoveIntoTheFolderAndClashingNamesGetASuffix() {
        Fixtures.write(context, "Дом", "- [ ] с телефона\n")
        Fixtures.write(context, "Работа", "- [ ] отчёт\n")
        File(vault, "дом.md").writeText("- [ ] с компьютера\n")
        assertTrue(storage.choose(tree))
        assertEquals("- [ ] с компьютера\n", File(vault, "дом.md").readText())
        assertEquals("- [ ] с телефона\n", File(vault, "Дом (телефон).md").readText())
        assertEquals("- [ ] отчёт\n", File(vault, "Работа.md").readText())
        assertFalse(Fixtures.file(context, "Дом").exists())
        assertEquals(tree, context.contentResolver.persistedUriPermissions.single().uri)
    }

    @Test fun keepingOnThePhoneCopiesTheFolderBackAndLetsItGo() {
        storage.choose(tree)
        File(vault, "Дом.md").writeText("- [ ] a\n")
        storage.keepOnPhone()
        assertNull(storage.chosen)
        assertEquals("- [ ] a\n", Fixtures.read(context, "Дом"))
        assertTrue(File(vault, "Дом.md").exists())
        assertEquals(emptyList(), context.contentResolver.persistedUriPermissions)
    }

    @Test fun aFolderThatIsGoneIsUnavailableAndChangesDoNothing() {
        storage.choose(tree)
        vault.deleteRecursively()
        val lists = TaskLists.of(context)
        assertFalse(lists.isAvailable)
        assertEquals(emptyList(), lists.names())
        assertNull(lists.change("Дом") { it.add("a", null) })
    }

    @Test fun aFolderWithoutPermissionIsUnavailable() {
        storage.choose(tree)
        context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertFalse(TaskLists.of(context).isAvailable)
    }

    @Test fun aFreeNameCountsOnAfterTheSuffix() {
        assertEquals("Дом", Storage.freeName("Дом", listOf("Работа"), " (телефон)"))
        assertEquals("Дом (телефон) 3", Storage.freeName("Дом", listOf("дом", "Дом (телефон)", "ДОМ (ТЕЛЕФОН) 2"), " (телефон)"))
    }
}
