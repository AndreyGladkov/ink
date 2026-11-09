package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class InkHeaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun aNestedScreenHeaderHasBackTitleActionsAndALine() {
        var backs = 0
        val header = InkHeader.of(context, "Настройки", { backs++ }, InkHeader.settings(context) {})
        render(header, height = 300)
        val minHeight = (InkHeader.MIN_HEIGHT_DP * context.resources.displayMetrics.density).roundToInt()
        assertTrue(header.height >= minHeight + 1, "${header.height}")
        header.find<InkButton>().single { it.contentDescription == "Назад" }.performClick()
        assertEquals(1, backs)
        assertTrue(header.find<InkButton>().any { it.contentDescription == "Настройки" })
        header.setBackgroundColor(Color.WHITE)
        assertPureBlackAndWhite(render(header, height = 300))
    }

    @Test fun theTitleCanBeChangedInPlace() {
        val header = InkHeader.of(context, "Избранное", null)
        InkHeader.setTitle(header, "Контакты")
        assertEquals(listOf("Контакты"), header.find<InkTextView>().map { it.text.toString() })
    }

    @Test fun backReturnsToTheStartOnlyWhileAway() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        var returns = 0
        val back = BackToStart(activity) { returns++ }
        assertFalse(back.onBackPressed())
        back.isAway = true
        assertTrue(back.onBackPressed())
        back.isAway = false
        assertFalse(back.onBackPressed())
        assertEquals(1, returns)
    }
}
