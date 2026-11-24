package io.github.andreygladkov.inktasks

import android.app.Application
import android.content.Intent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inktasks.lists.ListsActivity
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TaskLists
import io.github.andreygladkov.inktasks.store.TestDocumentsProvider
import io.github.andreygladkov.inktasks.task.TaskActivity
import io.github.andreygladkov.inktasks.ui.ListDots
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityTest {
    @get:Rule val temp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = """
        # Дом
        - [ ] Купить молоко 📅 2026-11-25
        	2,5%
        	- [ ] Заодно хлеб
        - [ ] Починить кран 📅 2026-11-20
        - [x] Позвонить маме ✅ 2026-11-23
        - [ ] Полить цветы
    """.trimIndent() + "\n"

    @Before fun fixToday() = Fixtures.fixToday()

    @After fun restore() = Fixtures.restoreClock()

    @Test fun openTasksAreListedWithSubtasksAndTheDoneOnesAreCounted() {
        Fixtures.write(context, "Дом", home)
        val activity = open().get()
        assertEquals(listOf("Купить молоко\n2,5%\nЗавтра", "Заодно хлеб", "Починить кран\n 20 нояб. ", "Полить цветы"), titles(activity))
        assertTrue(buttons(activity).any { it.text.toString() == "Выполнено (1)" })
        assertEquals("Дом", header(activity).text.toString())
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun anEmptyListSaysSoUnderTheDefaultName() {
        val activity = open().get()
        assertEquals("Мои задачи", header(activity).text.toString())
        val empty = contentRoot(activity).find<InkTextView>().single { it.text.toString() == "Задач нет" }
        assertEquals(View.VISIBLE, empty.visibility)
    }

    @Test fun aTapOnTheBoxCompletesTheTask() {
        Fixtures.write(context, "Дом", home)
        val activity = open().get()
        box(activity, "Отметить «Полить цветы»").performClick()
        assertTrue("- [x] Полить цветы ✅ 2026-11-24" in Fixtures.read(context, "Дом"))
        assertEquals(listOf("Купить молоко\n2,5%\nЗавтра", "Заодно хлеб", "Починить кран\n 20 нояб. "), titles(activity))
        assertTrue(buttons(activity).any { it.text.toString() == "Выполнено (2)" })
    }

    @Test fun aSubtaskIsCompletedInPlace() {
        Fixtures.write(context, "Дом", home)
        val activity = open().get()
        box(activity, "Отметить «Заодно хлеб»").performClick()
        assertTrue("\t- [x] Заодно хлеб ✅ 2026-11-24" in Fixtures.read(context, "Дом"))
        box(activity, "Снять отметку с «Заодно хлеб»").performClick()
        assertEquals(home, Fixtures.read(context, "Дом"))
    }

    @Test fun sortingByDateShowsTheNearestFirstWithoutChangingTheFile() {
        Fixtures.write(context, "Дом", home)
        TaskLists.of(context).setSort("Дом", TaskLists.Sort.DUE)
        val activity = open().get()
        assertEquals(listOf("Починить кран\n 20 нояб. ", "Купить молоко\n2,5%\nЗавтра", "Заодно хлеб", "Полить цветы"), titles(activity))
        assertEquals(home, Fixtures.read(context, "Дом"))
    }

    @Test fun changesFromAnotherDeviceAreShownOnReturn() {
        Fixtures.write(context, "Дом", home)
        val controller = open()
        controller.pause().stop()
        Fixtures.write(context, "Дом", "- [ ] С компьютера\n")
        controller.restart().resume()
        assertEquals(listOf("С компьютера"), titles(controller.get()))
    }

    @Test fun theHeaderOpensTheListsAndTheTaskScreens() {
        Fixtures.write(context, "Дом", home)
        val activity = open().get()
        header(activity).performClick()
        assertEquals(ListsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Новая задача" }.performClick()
        assertEquals(TaskActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        contentRoot(activity).firstOnPages<InkButton> { it.text.startsWith("Полить цветы") }.performClick()
        assertEquals(TaskActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    @Test fun theArrowsAtTheBottomGoThroughTheListsInTheirOrderAndRoundFromTheLastToTheFirstWithDotsForWhereYouAre() {
        Fixtures.write(context, "Дом", home)
        Fixtures.write(context, "Работа", "- [ ] Отправить отчёт\n")
        Fixtures.write(context, "Книги", "")
        val activity = open().get()
        assertEquals("Дом", header(activity).text.toString())
        assertEquals(listOf("Список 1 из 3"), dots(activity))
        switch(activity, "Следующий список").performClick()
        assertEquals("Книги", header(activity).text.toString())
        assertEquals(listOf("Список 2 из 3"), dots(activity))
        switch(activity, "Следующий список").performClick()
        assertEquals("Работа", header(activity).text.toString())
        assertEquals(listOf("Отправить отчёт"), titles(activity))
        switch(activity, "Следующий список").performClick()
        assertEquals("Дом", header(activity).text.toString())
        switch(activity, "Предыдущий список").performClick()
        assertEquals("Работа", header(activity).text.toString())
        assertEquals("Работа", TaskLists.of(context).current)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theArrowsAndDotsAreHiddenWithOneListAndWhileMoving() {
        Fixtures.write(context, "Дом", home)
        val alone = open().get()
        assertEquals(View.GONE, switch(alone, "Следующий список").visibility)
        assertEquals(emptyList(), dots(alone))
        Fixtures.write(context, "Работа", "")
        val milk = TaskLists.of(context).read("Дом").tasks[0].ref
        val moving = open(MainActivity.move(context, milk)).get()
        assertEquals(View.GONE, switch(moving, "Следующий список").visibility)
        assertEquals(View.GONE, switch(moving, "Предыдущий список").visibility)
        assertEquals(emptyList(), dots(moving))
    }

    @Test fun aMovedTaskGoesWhereTheMarkIsTapped() {
        Fixtures.write(context, "Дом", home)
        val milk = TaskLists.of(context).read("Дом").tasks[0].ref
        val activity = open(MainActivity.move(context, milk)).get()
        assertEquals("Куда переместить?", header(activity).text.toString())
        val marks = buttons(activity).filter { it.text.toString() == "Сюда" }
        assertEquals(2, marks.size)
        assertPureBlackAndWhite(render(contentRoot(activity)))
        marks.last().performClick()
        assertEquals(home.replace("- [ ] Купить молоко 📅 2026-11-25\n\t2,5%\n\t- [ ] Заодно хлеб\n", "")
            .replace("- [ ] Полить цветы\n", "- [ ] Полить цветы\n- [ ] Купить молоко 📅 2026-11-25\n\t2,5%\n\t- [ ] Заодно хлеб\n"), Fixtures.read(context, "Дом"))
        assertEquals("Дом", header(activity).text.toString())
    }

    @Test fun backLeavesMovingWithoutAMove() {
        Fixtures.write(context, "Дом", home)
        val milk = TaskLists.of(context).read("Дом").tasks[0].ref
        val activity = open(MainActivity.move(context, milk)).get()
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertEquals("Дом", header(activity).text.toString())
        assertEquals(home, Fixtures.read(context, "Дом"))
        assertTrue(!activity.isFinishing)
    }

    @Test fun aFolderThatIsGoneIsSaidAndCanBeChosenAgain() {
        val tree = TestDocumentsProvider.install(temp.root, "Tasks")
        Storage(context).choose(tree)
        File(temp.root, "Tasks").deleteRecursively()
        val activity = open().get()
        assertTrue(contentRoot(activity).find<InkTextView>().any { it.text.toString() == "Нет доступа к папке" && it.visibility == View.VISIBLE })
        val pick = contentRoot(activity).find<InkButton>().single { it.text.toString() == "Выбрать папку…" }
        assertEquals(View.VISIBLE, pick.visibility)
        assertEquals(View.GONE, contentRoot(activity).find<InkButton>().single { it.contentDescription == "Новая задача" }.visibility)
        pick.performClick()
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, shadowOf(activity).nextStartedActivityForResult.intent.action)
    }

    private fun open(intent: Intent = Intent(context, MainActivity::class.java)): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()

    private fun header(activity: MainActivity) = contentRoot(activity).find<InkButton>().first()

    private fun buttons(activity: MainActivity) = contentRoot(activity).findOnPages<InkButton>()

    private fun titles(activity: MainActivity) = buttons(activity)
        .filter { it.visibility == View.VISIBLE && it.contentDescription == null && it.isClickable && it.icon == 0 && it.text.isNotEmpty() && it.text.toString() !in setOf("Сюда") && !it.text.startsWith("Выполнено") }
        .map { it.text.toString() }

    private fun switch(activity: MainActivity, description: String) = contentRoot(activity).find<InkButton>().single { it.contentDescription == description }

    private fun dots(activity: MainActivity) = contentRoot(activity).find<ListDots>().filter { it.visibility == View.VISIBLE && it.contentDescription != null }.map { it.contentDescription.toString() }

    private fun box(activity: MainActivity, description: String) = contentRoot(activity).firstOnPages<InkButton> { it.contentDescription == description }
}
