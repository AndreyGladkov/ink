package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class InkButtonRowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val width = 600

    @Test fun aLabelChangedAfterLayoutIsFittedAgain() {
        val pause = button("Пауза")
        val row = row(pause, button("+1 мин"), button("Сброс"))
        render(row, width = width, height = 200)
        assertEquals(1f, pause.textScale)

        pause.text = "Продолжить работу"
        assertTrue(row.isLayoutRequested)
        render(row, width = width, height = 200)
        assertTrue(pause.textScale < 1f, "${pause.textScale}")
        assertFits(pause)

        pause.text = "Пауза"
        render(row, width = width, height = 200)
        assertEquals(1f, pause.textScale)
    }

    @Test fun aHiddenButtonLeavesItsWidthToTheShownOnes() {
        val main = button("Начать перерыв")
        val skip = button("Пропустить")
        val row = row(main, skip)
        render(row, width = width, height = 200)
        val shared = main.textScale

        skip.visibility = View.GONE
        render(row, width = width, height = 200)
        assertTrue(main.textScale > shared, "${main.textScale} after $shared")
        assertFits(main)
    }

    private fun assertFits(button: InkButton) {
        val available = button.width - button.paddingLeft - button.paddingRight
        assertTrue(button.paint.measureText(button.text.toString()) <= available, "'${button.text}' overflows $available px")
    }

    private fun button(label: String) = InkButton(context).apply { text = label }

    private fun row(vararg buttons: InkButton) = InkButtonRow(context, 8, 1f).apply { buttons.forEach(::addButton) }
}
