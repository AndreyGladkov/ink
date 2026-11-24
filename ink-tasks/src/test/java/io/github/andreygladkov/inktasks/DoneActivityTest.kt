package io.github.andreygladkov.inktasks

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class DoneActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = "- [x] Позвонить маме ✅ 2026-11-23\n- [ ] Полить цветы\n- [x] Вынести мусор\n"

    @Before fun setUp() {
        Fixtures.fixToday()
        Fixtures.write(context, "Дом", home)
    }

    @After fun restore() = Fixtures.restoreClock()

    @Test fun doneTasksAreListedWithTheirDay() {
        val activity = open()
        assertEquals(listOf("Позвонить маме\nВыполнено вчера", "Вынести мусор"), titles(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theBoxReturnsATaskToTheList() {
        val activity = open()
        contentRoot(activity).firstOnPages<InkButton> { it.contentDescription == "Снять отметку с «Позвонить маме»" }.performClick()
        assertEquals("- [ ] Позвонить маме\n- [ ] Полить цветы\n- [x] Вынести мусор\n", Fixtures.read(context, "Дом"))
        assertEquals(listOf("Вынести мусор"), titles(activity))
    }

    @Test fun allDoneTasksAreDeletedOnASecondTap() {
        val activity = open()
        val delete = contentRoot(activity).firstOnPages<InkButton> { it.text.toString() == "Удалить выполненные" }
        delete.performClick()
        assertEquals(home, Fixtures.read(context, "Дом"))
        delete.performClick()
        assertEquals("- [ ] Полить цветы\n", Fixtures.read(context, "Дом"))
        assertTrue(contentRoot(activity).findOnPages<InkTextView>().any { it.text.toString() == "Выполненных нет" })
    }

    private fun open() = Robolectric.buildActivity(DoneActivity::class.java, DoneActivity.intent(context, "Дом")).setup().get()

    private fun titles(activity: DoneActivity) = contentRoot(activity).findOnPages<InkButton>()
        .filter { it.icon == 0 && it.contentDescription == null && it.text.isNotEmpty() && it.text.toString() != "Удалить выполненные" }
        .map { it.text.toString() }
}
