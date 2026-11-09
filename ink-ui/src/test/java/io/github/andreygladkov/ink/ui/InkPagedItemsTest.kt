package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.find
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class InkPagedItemsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun aSectionTitleSharesItsRowWithTheItemAfterIt() {
        val items = InkPagedItems(context).apply {
            item { text("loose") }
            section("First")
            item { text("a") }
            item { text("b") }
            section("Empty")
            section("Last")
        }
        assertEquals(listOf(listOf("loose"), listOf("First", "a"), listOf("b"), listOf("Empty"), listOf("Last")),
            (0 until items.count).map { texts(items.createView(it)) })
    }

    @Test fun eachRowIsBuiltAgainWhenShown() {
        var built = 0
        val items = InkPagedItems(context).apply { item { built++; text("row") } }
        items.createView(0)
        items.createView(0)
        assertEquals(2, built)
    }

    @Test fun preformattedTextKeepsEachLineAndItsIndentAsARow() {
        val items = InkPagedItems(context).apply { preformatted("\n   Title\nFirst line   \n  second\n\n  \nNext\n") }
        assertEquals(listOf("   Title", "First line", "  second", "Next"), (0 until items.count).map { texts(items.createView(it)).single() })
    }

    @Test fun aRowIsFoundByItsKeyAfterRowsElsewhereChange() {
        val before = InkPagedItems(context).apply {
            section("Blocked")
            item { text("+1") }
            section("About")
            item { text("about") }
        }
        val after = InkPagedItems(context).apply {
            section("Blocked")
            item { text("+1") }
            item { text("+2") }
            item { text("+3") }
            section("About")
            item { text("about") }
        }
        assertEquals(3, after.positionOf(before.keyAt(1)!!))
        assertEquals(null, after.positionOf("Gone#0"))
    }

    private fun text(value: String) = TextView(context).apply { text = value }

    private fun texts(view: View) = view.find<TextView>().map { it.text.toString() }
}
