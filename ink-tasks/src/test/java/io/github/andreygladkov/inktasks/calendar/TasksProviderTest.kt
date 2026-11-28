package io.github.andreygladkov.inktasks.calendar

import android.app.Application
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.remind.Reminders
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.task.TaskActivity
import io.github.andreygladkov.inktasks.tasks.TaskRef
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class TasksProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = """
        # Дом
        - [ ] Купить молоко 📅 2026-11-25
        	- [ ] Заодно хлеб ⏰ 2026-11-25 9:30 📅 2026-11-25
        	- [x] Сдать бутылки 📅 2026-11-25 ✅ 2026-11-24
        - [x] Позвонить маме 📅 2026-11-24 ✅ 2026-11-23
        	- [ ] Спросить про дачу 📅 2026-11-24
        - [ ] Починить кран
    """.trimIndent() + "\n"

    @Before fun setUp() {
        Fixtures.fixToday()
        Fixtures.write(context, "Дом", home)
        Fixtures.write(context, "Работа", "- [ ] Отчёт ⏰ 2026-11-26 15:00 📅 2026-11-27\n")
        Robolectric.setupContentProvider(TasksProvider::class.java, "${context.packageName}.tasks")
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun openTasksAndSubtasksWithADueDateAreGivenWithTheirListAndReminder() {
        val rows = context.contentResolver.query(TasksProvider.uri(context), null, null, null, null)!!.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).associate { cursor.getColumnName(it) to cursor.getString(it) })
                }
            }
        }
        assertEquals(listOf("Купить молоко", "Заодно хлеб", "Отчёт"), rows.map { it["title"] })
        assertEquals(listOf("Дом", "Дом", "Работа"), rows.map { it["list"] })
        assertEquals(listOf("2026-11-25", "2026-11-25", "2026-11-27"), rows.map { it["due"] })
        assertEquals(listOf(null, "2026-11-25T09:30", "2026-11-26T15:00"), rows.map { it["reminder"] })
        assertEquals("\t- [ ] Заодно хлеб ⏰ 2026-11-25 9:30 📅 2026-11-25", rows[1]["line"])
        assertEquals("0", rows[1]["occurrence"])
    }

    @Test fun onlyTheDueTasksUriIsServed() {
        assertFailsWith<IllegalArgumentException> {
            context.contentResolver.query(Uri.parse("content://${context.packageName}.tasks/all"), null, null, null, null)
        }
    }

    @Test fun theCalendarHearsOfChangesToDueTasksButNotToOthers() {
        val changes = mutableListOf<Uri?>()
        context.contentResolver.registerContentObserver(TasksProvider.uri(context), false, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changes += uri
            }
        })
        val lists = TaskLists.of(context)
        lists.change("Дом") { it.add("Вынести мусор", due = null) }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes.isEmpty())
        lists.change("Дом") { it.setDue(TaskRef("- [ ] Починить кран", 0), LocalDate.of(2026, 11, 30)) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, changes.size)
        lists.delete("Работа")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, changes.size)
    }

    @Test fun theCalendarHearsOnceOfDueTasksASyncBrought() {
        val changes = mutableListOf<Uri?>()
        context.contentResolver.registerContentObserver(TasksProvider.uri(context), false, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                changes += uri
            }
        })
        Reminders.update(context)
        shadowOf(Looper.getMainLooper()).idle()
        changes.clear()
        Reminders.update(context)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(changes.isEmpty())
        Fixtures.write(context, "Работа", "- [ ] Отчёт ⏰ 2026-11-26 15:00 📅 2026-11-28\n")
        Reminders.update(context)
        Reminders.update(context)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, changes.size)
    }

    @Test fun theCalendarOpensATaskOnItsOwnTaskScreen() {
        val intent = Intent("io.github.andreygladkov.inktasks.action.OPEN_TASK")
            .putExtra("list", "Работа")
            .putExtra("line", "- [ ] Отчёт ⏰ 2026-11-26 15:00 📅 2026-11-27")
            .putExtra("occurrence", 0)
        val activity = Robolectric.buildActivity(OpenTaskActivity::class.java, intent).setup().get()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(TaskActivity::class.java.name, started.component?.className)
        assertEquals("Работа", started.getStringExtra("list"))
        assertEquals("- [ ] Отчёт ⏰ 2026-11-26 15:00 📅 2026-11-27", started.getStringExtra("line"))
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(activity.isFinishing)
    }
}
