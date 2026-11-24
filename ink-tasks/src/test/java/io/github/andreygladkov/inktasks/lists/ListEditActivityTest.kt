package io.github.andreygladkov.inktasks.lists

import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.store.TaskLists
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ListEditActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val lists = TaskLists.of(context)

    @Test fun aNewListGetsAFileAndOpens() {
        val activity = create()
        field(activity).setText("Работа")
        button(activity, "Готово").performClick()
        assertTrue(Fixtures.file(context, "Работа").exists())
        assertEquals("Работа", lists.current)
        assertTrue(activity.isFinishing)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun charactersObsidianRefusesCannotBeTyped() {
        val activity = create()
        field(activity).setText("Дом/дача: #1")
        assertEquals("Домдача 1", field(activity).text.toString())
    }

    @Test fun aTakenNameIsRefusedWhateverItsCase() {
        Fixtures.write(context, "Дом", "")
        val activity = create()
        field(activity).setText("дом")
        button(activity, "Готово").performClick()
        assertEquals(View.VISIBLE, contentRoot(activity).find<InkTextView>().single { it.text.toString() == "Такой список уже есть" }.visibility)
        assertFalse(activity.isFinishing)
    }

    @Test fun aListIsRenamedWithItsSort() {
        Fixtures.write(context, "Дом", "- [ ] a\n")
        val activity = edit("Дом")
        button(activity, "По сроку").performClick()
        field(activity).setText("Дача")
        button(activity, "Готово").performClick()
        assertEquals("- [ ] a\n", Fixtures.read(context, "Дача"))
        assertEquals(TaskLists.Sort.DUE, lists.sort("Дача"))
        assertTrue(activity.isFinishing)
    }

    @Test fun theDefaultListWithoutAFileIsCreatedUnderTheNewName() {
        val activity = edit("Мои задачи")
        assertTrue(contentRoot(activity).find<InkButton>().none { it.text.toString() == "Удалить список" })
        field(activity).setText("Входящие")
        button(activity, "Готово").performClick()
        assertEquals(listOf("Входящие"), lists.names())
        assertEquals("Входящие", lists.current)
    }

    @Test fun deletingAsksForASecondTap() {
        Fixtures.write(context, "Дом", "- [ ] a\n")
        val activity = edit("Дом")
        val delete = button(activity, "Удалить список")
        delete.performClick()
        assertTrue(Fixtures.file(context, "Дом").exists())
        delete.performClick()
        assertFalse(Fixtures.file(context, "Дом").exists())
        assertTrue(activity.isFinishing)
    }

    private fun create() = Robolectric.buildActivity(ListEditActivity::class.java, ListEditActivity.create(context)).setup().get()

    private fun edit(name: String) = Robolectric.buildActivity(ListEditActivity::class.java, ListEditActivity.edit(context, name)).setup().get()

    private fun field(activity: ListEditActivity) = contentRoot(activity).find<InkEditText>().single()

    private fun button(activity: ListEditActivity, label: String) = contentRoot(activity).find<InkButton>().single { it.text.toString() == label }
}
