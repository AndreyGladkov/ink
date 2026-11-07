package io.github.andreygladkov.inklauncher.ui

import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.LetterIndexView
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.icons.PackIcon

/** Pages of pack icons with their names; a group longer than a few pages gets a letter index to jump by. */
class IconGrid(
    private val activity: HomeActivity,
    private val pack: String,
    private val onPick: (PackIcon) -> Unit,
) : LinearLayout(activity) {
    private val style = InkStyle.of(activity)
    private val grid = CellGrid(activity, COLUMNS, ROWS)
    private val index = LetterIndexView(activity) { letter -> jumpTo(letter) }
    private val bar = PagerBar(activity, { flip(page - 1) }, { flip(page + 1) })
    private var icons: List<PackIcon> = emptyList()
    private var page = 0

    private val pages get() = ((icons.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)

    init {
        orientation = VERTICAL
        addView(LinearLayout(activity).apply {
            orientation = HORIZONTAL
            addView(grid, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(index, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(icons: List<PackIcon>) {
        this.icons = icons
        page = 0
        index.letters = if (pages > LETTERS_FROM_PAGES) icons.map(::letter).distinct() else emptyList()
        index.visibility = if (index.letters.isEmpty()) GONE else VISIBLE
        fill()
    }

    private fun fill() {
        grid.removeAllViews()
        icons.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { position, icon ->
            grid.add(TileView(activity, icon.label) { size ->
                activity.icons.packIcon(pack, icon.drawable, size) ?: activity.icons.text("?", size, style)
            }.apply {
                contentDescription = icon.label
                setOnClickListener { onPick(icon) }
            }, position % COLUMNS, position / COLUMNS)
        }
        index.current = icons.getOrNull(page * PAGE_SIZE)?.let(::letter)
        bar.show(page, pages, hasPrev = page > 0, hasNext = page < pages - 1)
    }

    private fun flip(target: Int) {
        if (target !in 0 until pages || target == page) return
        page = target
        fill()
        A9Panel.onPageFlipped(activity)
    }

    private fun jumpTo(letter: String) {
        val position = icons.indexOfFirst { letter(it) == letter }
        if (position >= 0) flip(position / PAGE_SIZE)
    }

    private fun letter(icon: PackIcon) = LetterIndexView.letterOf(icon.label)

    private companion object {
        const val COLUMNS = 4
        const val ROWS = 6
        const val PAGE_SIZE = COLUMNS * ROWS
        const val LETTERS_FROM_PAGES = 3
    }
}
