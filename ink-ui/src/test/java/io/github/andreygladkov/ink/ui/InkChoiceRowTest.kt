package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class InkChoiceRowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val borderPx = (InkStyle.LINE_DP * context.resources.displayMetrics.density).roundToInt()

    @Test fun joinedOptionsShareTheLineBetweenThem() {
        val row = row(isJoined = true).apply { setBackgroundColor(Color.WHITE) }
        assertPureBlackAndWhite(render(row, height = 300))
        val buttons = row.find<InkButton>()
        buttons.zipWithNext().forEach { (left, right) -> assertEquals(left.right - borderPx, right.left) }
        assertEquals(listOf(false to true, true to true, true to false), buttons.map { it.isJoinedStart to it.isJoinedEnd })
    }

    @Test fun separateOptionsKeepAGapAndTheirOwnCorners() {
        val row = row(isJoined = false)
        render(row, height = 300)
        val buttons = row.find<InkButton>()
        buttons.zipWithNext().forEach { (left, right) -> assertTrue(right.left > left.right) }
        assertTrue(buttons.none { it.isJoinedStart || it.isJoinedEnd })
    }

    @Test fun choosingMovesTheSelection() {
        var chosen = 0
        val row = InkChoiceRow(context, "Title", listOf(1 to "One", 2 to "Two"), 1) { chosen = it }
        row.find<InkButton>()[1].performClick()
        assertEquals(2, chosen)
        assertEquals(listOf(false, true), row.find<InkButton>().map { it.isSelected })
    }

    private fun row(isJoined: Boolean) =
        InkChoiceRow(context, "Title", listOf(1 to "One", 2 to "Two", 3 to "Three"), 2, isJoined = isJoined) {}
}
