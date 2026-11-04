package io.github.andreygladkov.ink.eink

import android.content.Context
import android.graphics.Color
import android.text.Spanned
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkTextView
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
class MonochromeEmojiTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun findsEmojiClustersButNotPlainSymbols() {
        assertEquals(listOf(0 to 2), MonochromeEmoji.emojiClusters("🍔 Константин"))
        assertEquals(listOf(5 to 7), MonochromeEmoji.emojiClusters("Мама ❤️"))
        assertEquals(listOf(0 to 4), MonochromeEmoji.emojiClusters("🇰🇿"))
        assertEquals(listOf(0 to 3), MonochromeEmoji.emojiClusters("1️⃣"))
        assertEquals(listOf(0 to 11), MonochromeEmoji.emojiClusters("👨‍👩‍👧‍👦"))
        assertEquals(emptyList(), MonochromeEmoji.emojiClusters("Иван 112 © ♥ #1"))
    }

    @Test fun plainTextIsLeftAlone() {
        val text = "Иван Петров"
        assertTrue(MonochromeEmoji.apply(context, text) === text)
    }

    @Test fun emojiGetsMonochromeSpans() {
        val styled = MonochromeEmoji.apply(context, "Мама ❤️ 🍔") as Spanned
        assertTrue(styled.getSpans(0, styled.length, Any::class.java).size >= 2)
        assertTrue("️" !in styled.toString())
    }

    @Test fun nameWithEmojiRendersInPureBlackAndWhite() {
        val view = InkTextView(context).apply {
            text = "🍔 Константин 🇰🇿 👨‍👩‍👧‍👦"
            setBackgroundColor(Color.WHITE)
        }
        val bitmap = render(view, 824, 120)
        assertPureBlackAndWhite(bitmap)
        val emojiArea = IntArray(60 * 60).also { bitmap.getPixels(it, 0, 60, 0, 30, 60, 60) }
        assertTrue(emojiArea.any { it == Color.BLACK }, "burger was not drawn")
        assertTrue(bitmap.pixels().count { it == Color.BLACK } > 200)
    }
}
