package io.github.andreygladkov.ink.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

/**
 * The rows of an [InkPagedActivity]. A row's view is built again each time its page is shown, so [item] reads its
 * state when called. A section title is kept on the page of the row after it.
 */
class InkPagedItems(private val context: Context) : InkPagedList.Adapter {
    private class Item(val key: String, val section: String?, val create: (() -> View)?)

    private val items = mutableListOf<Item>()
    private var currentSection = ""
    private var rowInSection = 0

    fun section(title: String) {
        currentSection = title
        rowInSection = 0
        items += Item(nextKey(), title, null)
    }

    fun item(create: () -> View) {
        val last = items.lastOrNull()
        if (last != null && last.create == null) items[items.lastIndex] = Item(last.key, last.section, create)
        else items += Item(nextKey(), null, create)
    }

    /** Hard-wrapped text keeps its lines and their indentation; each line is a row, so a long paragraph can span pages. */
    fun preformatted(text: String) {
        val lines = text.lines().map(String::trimEnd)
        lines.forEachIndexed { index, line ->
            if (line.isEmpty()) return@forEachIndexed
            val endsParagraph = lines.getOrNull(index + 1).isNullOrEmpty()
            item {
                InkTextView(context).apply {
                    this.text = line
                    textScale = InkStyle.SECONDARY_SCALE
                    if (endsParagraph) setPadding(0, 0, 0, dp(PARAGRAPH_GAP_DP))
                }
            }
        }
    }

    /** Names a row by its section and its place there, so it is found again after rows elsewhere come or go. */
    fun keyAt(position: Int): String? = items.getOrNull(position)?.key

    fun positionOf(key: String): Int? = items.indexOfFirst { it.key == key }.takeIf { it >= 0 }

    override val count: Int get() = items.size

    override fun itemId(position: Int) = position.toLong()

    override fun createView(position: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(PADDING_DP), 0, dp(PADDING_DP), 0)
        val item = items[position]
        item.section?.let(::addSection)
        item.create?.invoke()?.let(::addView)
    }

    private fun nextKey() = "$currentSection#${rowInSection++}"

    private fun dp(value: Float) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 16f
        const val PARAGRAPH_GAP_DP = 8f
    }
}
