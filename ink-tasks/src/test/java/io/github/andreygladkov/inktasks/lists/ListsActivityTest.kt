package io.github.andreygladkov.inktasks.lists

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.store.TaskLists
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class ListsActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun listsAreInAlphabeticalOrderWithTheCurrentOneBold() {
        listOf("Работа", "Дом").forEach { Fixtures.write(context, it, "") }
        TaskLists.of(context).open("Работа")
        val activity = open()
        assertEquals(listOf("Дом" to false, "Работа" to true), names(activity).map { it.text.toString() to it.isBold })
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theDefaultListIsShownBeforeItHasAFile() {
        assertEquals(listOf("Мои задачи"), names(open()).map { it.text.toString() })
    }

    @Test fun aTapOpensTheList() {
        listOf("Работа", "Дом").forEach { Fixtures.write(context, it, "") }
        val activity = open()
        names(activity).single { it.text.toString() == "Работа" }.performClick()
        assertEquals("Работа", TaskLists.of(context).current)
        assertTrue(activity.isFinishing)
    }

    @Test fun thePencilAndNewListOpenTheListScreen() {
        Fixtures.write(context, "Дом", "")
        val activity = open()
        contentRoot(activity).firstOnPages<InkButton> { it.contentDescription == "Изменить «Дом»" }.performClick()
        assertEquals("Дом", shadowOf(activity).nextStartedActivity.getStringExtra("name"))
        contentRoot(activity).firstOnPages<InkButton> { it.text.toString() == "Новый список" }.performClick()
        assertEquals(ListEditActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
    }

    private fun open() = Robolectric.buildActivity(ListsActivity::class.java).setup().get()

    private fun names(activity: ListsActivity) = contentRoot(activity).findOnPages<InkButton>()
        .filter { it.icon == 0 && it.contentDescription == null && it.text.isNotEmpty() }
}
