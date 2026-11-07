package io.github.andreygladkov.inklauncher.ui

import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.FolderTile

class FolderScreen(private val activity: HomeActivity, private val folderId: Long) : LinearLayout(activity), SideScreen {
    private val spec = activity.layout.spec
    private val rows = (spec.rows - 1).coerceAtLeast(1)
    private val grid = CellGrid(activity, spec.columns, rows)
    private val bar = PagerBar(activity, ::prev, ::next)
    private val header = screenHeader(activity, "", headerButton(activity, R.drawable.ic_arrow_back, R.string.back) { activity.showHome() })
    private var page = 0

    init {
        orientation = VERTICAL
        addView(header)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refresh()
    }

    private val folder: FolderTile? get() = activity.layout.find(folderId)?.tile as? FolderTile

    private val capacity get() = spec.columns * rows

    override fun refresh() {
        val folder = folder ?: return activity.showHome()
        header.setHeaderTitle(folder.name)
        val pages = ((folder.apps.size + capacity - 1) / capacity).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        grid.removeAllViews()
        folder.apps.drop(page * capacity).take(capacity).forEachIndexed { index, app ->
            grid.add(activity.tileView(app).apply {
                setOnClickListener { activity.launch(app.app) }
                setOnLongClickListener {
                    activity.menus.folderApp(app)
                    true
                }
            }, index % spec.columns, index / spec.columns)
        }
        bar.show(page, pages, hasPrev = page > 0, hasNext = page < pages - 1)
    }

    fun next() = flip(page + 1)

    fun prev() = flip(page - 1)

    private fun flip(target: Int) {
        val pages = ((folder?.apps?.size ?: 0) + capacity - 1) / capacity
        if (target !in 0 until pages || target == page) return
        page = target
        refresh()
        A9Panel.onPageFlipped(activity)
    }
}
