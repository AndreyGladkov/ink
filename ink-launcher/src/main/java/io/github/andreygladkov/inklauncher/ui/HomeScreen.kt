package io.github.andreygladkov.inklauncher.ui

import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.FolderTile
import io.github.andreygladkov.inklauncher.home.GridSpec
import io.github.andreygladkov.inklauncher.home.HomeLayout
import io.github.andreygladkov.inklauncher.home.LabeledTile
import io.github.andreygladkov.inklauncher.home.Placed
import io.github.andreygladkov.inklauncher.home.ScreenTimeTile
import io.github.andreygladkov.inklauncher.home.Sheet
import io.github.andreygladkov.inklauncher.home.Spot
import io.github.andreygladkov.inklauncher.home.WidgetTile
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import io.github.andreygladkov.inklauncher.notify.NotificationGroups

/**
 * Pinned rows on top, the paged rows, the pinned rows below and the arrows at the bottom edge, where the side
 * screens keep their way back. Only the paged grid is rebuilt when the page changes, so the rest of the panel
 * keeps its pixels.
 */
class HomeScreen(private val activity: HomeActivity) : LinearLayout(activity) {
    private val bar = PagerBar(activity, ::prev, ::next) { activity.showAllApps() }
    private var spec: GridSpec? = null
    private var top: CellGrid? = null
    private var paged: CellGrid? = null
    private var bottom: CellGrid? = null
    private var layout = HomeLayout()

    var page = 0
        private set

    var movingId: Long? = null
        private set

    init {
        orientation = VERTICAL
    }

    fun bind(layout: HomeLayout) {
        this.layout = layout
        if (layout.spec != spec) build(layout.spec)
        page = page.coerceIn(0, layout.pageCount - 1)
        movingId?.let { if (layout.find(it) == null) endMove() }
        fill(top, Sheet.Top)
        fill(paged, Sheet.Page(page))
        fill(bottom, Sheet.Bottom)
        updateBar()
    }

    fun showPage(index: Int) {
        val target = index.coerceIn(0, layout.pageCount - 1)
        if (target == page) return
        val leaving = page
        page = target
        if (layout.pageCount > 1 && layout.on(Sheet.Page(leaving)).isEmpty()) {
            activity.update(keepPage = target) { it }
        } else {
            fill(paged, Sheet.Page(page))
            updateBar()
        }
        A9Panel.onPageFlipped(context)
    }

    fun addPage() {
        activity.update(keepPage = page + 1) { it.withPageAfter(page) }
        A9Panel.onPageFlipped(context)
    }

    fun followPage(index: Int) {
        page = index
    }

    fun cellSize(): Pair<Int, Int> {
        val grid = paged
        if (grid != null && grid.cellWidth > 0) return grid.cellWidth to grid.cellHeight
        val metrics = resources.displayMetrics
        val columns = layout.spec.columns
        return metrics.widthPixels / columns to metrics.widthPixels / columns
    }

    fun prev() {
        when {
            page > 0 -> showPage(page - 1)
            movingId == null -> activity.showNotifications()
        }
    }

    fun next() {
        when {
            page < layout.pageCount - 1 -> showPage(page + 1)
            movingId != null -> addPage()
            else -> activity.showAllApps()
        }
    }

    fun startMove(id: Long) {
        movingId = id
        bar.showMoving(context.getString(R.string.move_hint), ::cancelMove)
        bind(layout)
    }

    fun cancelMove(): Boolean {
        if (movingId == null) return false
        endMove()
        bind(layout)
        return true
    }

    fun updateBar() {
        val count = if (page == 0 && movingId == null) NotificationGroups.loudCount(NotificationFeed.notifications) else 0
        bar.show(page, layout.pageCount, hasPrev = page > 0 || movingId == null, hasNext = true, prevCount = count)
    }

    private fun endMove() {
        movingId = null
        bar.showMoving(null) {}
    }

    private fun build(spec: GridSpec) {
        this.spec = spec
        removeAllViews()
        top = spec.pinnedTop.takeIf { it > 0 }?.let { rows -> grid(spec.columns, rows) { Sheet.Top }.also { addGrid(it, rows) } }
        paged = grid(spec.columns, spec.pagedRows) { Sheet.Page(page) }.also { addGrid(it, spec.pagedRows) }
        bottom = spec.pinnedBottom.takeIf { it > 0 }?.let { rows -> grid(spec.columns, rows) { Sheet.Bottom }.also { addGrid(it, rows) } }
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun addGrid(grid: CellGrid, rows: Int) = addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, rows.toFloat()))

    private fun grid(columns: Int, rows: Int, sheet: () -> Sheet) = CellGrid(context, columns, rows).apply {
        onCellTap = { column, row -> cellTapped(Spot(sheet(), column, row)) }
        onCellLongPress = { column, row -> if (movingId == null) activity.menus.emptyCell(Spot(sheet(), column, row)) }
    }

    private fun fill(grid: CellGrid?, sheet: Sheet) {
        grid ?: return
        grid.removeAllViews()
        layout.on(sheet).forEach { placed ->
            grid.add(view(placed), placed.spot.column, placed.spot.row, placed.spot.width, placed.spot.height)
        }
    }

    private fun view(placed: Placed): View = when (val tile = placed.tile) {
        is LabeledTile -> activity.tileView(tile).apply { isSelected = tile.id == movingId }.withMenu(placed)
        is WidgetTile -> activity.widgets.view(tile.widgetId) ?: activity.missingWidget(tile).withMenu(placed)
        is ScreenTimeTile -> activity.screenTimeView().apply { isSelected = tile.id == movingId }.withMenu(placed)
    }

    private fun View.withMenu(placed: Placed) = apply {
        setOnClickListener { tapped(placed) }
        setOnLongClickListener {
            if (movingId == null) activity.menus.tile(placed)
            true
        }
    }

    fun screenTimeViews(): List<ScreenTimeView> = listOfNotNull(top, paged, bottom).flatMap { grid ->
        (0 until grid.childCount).mapNotNull { grid.getChildAt(it) as? ScreenTimeView }
    }

    private fun tapped(placed: Placed) {
        val moving = movingId
        if (moving != null) {
            if (moving == placed.tile.id) return
            val dropped = layout.drop(moving, placed.tile.id, context.getString(R.string.folder_default_name)) ?: return
            endMove()
            activity.update { dropped }
            return
        }
        when (val tile = placed.tile) {
            is AppTile -> activity.launch(tile.app)
            is FolderTile -> activity.openFolder(tile.id)
            is ScreenTimeTile -> activity.openScreenTime()
            is WidgetTile -> Unit
        }
    }

    private fun cellTapped(spot: Spot) {
        val moving = movingId ?: return
        val current = layout.find(moving) ?: return
        val target = spot.copy(width = current.spot.width, height = current.spot.height)
        if (!layout.fits(target, ignoring = moving)) return
        endMove()
        activity.update { it.move(moving, target) }
        (layout.find(moving)?.spot?.sheet as? Sheet.Page)?.let { showPage(it.index) }
    }
}
