package io.github.andreygladkov.inktasks.settings

import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkTextView
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    @Test fun settingsHaveTheDisplayHapticsAndAboutSections() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        val texts = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }
        assertTrue(listOf("Отображение", "Вибрация", "О приложении").all { it in texts }, "$texts")
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aboutNamesTheAppAndWhatItDoesWithData() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val texts = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }
        assertTrue(texts.any { it.startsWith("Ink Tasks не ходит в интернет") }, "$texts")
    }
}
