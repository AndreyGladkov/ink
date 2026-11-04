package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class LetterIndexViewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density = context.resources.displayMetrics.density

    @Test fun indexIsSeparatedBySpaceNotByLine() {
        val index = LetterIndexView(context) {}.apply {
            setBackgroundColor(Color.WHITE)
            letters = listOf("А", "Б", "В")
        }
        val bitmap = render(index, width = (48 * density).toInt(), height = 600)
        assertPureBlackAndWhite(bitmap)
        assertTrue((0 until bitmap.height).all { bitmap.getPixel(0, it) == Color.WHITE })
    }

    @Test fun currentLetterIsInvertedAcrossTheWholeSlot() {
        val index = LetterIndexView(context) {}.apply {
            setBackgroundColor(Color.WHITE)
            letters = listOf("А", "Б", "В")
            current = "Б"
        }
        val bitmap = render(index, width = (48 * density).toInt(), height = 600)
        assertTrue(bitmap.getPixel(0, 300) == Color.BLACK && bitmap.getPixel(bitmap.width - 1, 300) == Color.BLACK)
        assertTrue(bitmap.getPixel(0, 100) == Color.WHITE)
    }
}
