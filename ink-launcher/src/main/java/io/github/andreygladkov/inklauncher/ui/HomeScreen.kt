package io.github.andreygladkov.inklauncher.ui

import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import io.github.andreygladkov.ink.inkPrefs
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
import io.github.andreygladkov.inklauncher.home.Tile
import io.github.andreygladkov.inklauncher.home.WidgetTile
import io.github.andreygladkov.inklauncher.notify.NotificationBadge
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
        val count = if (movingId == null) NotificationGroups.loudCount(NotificationFeed.notifications) else 0
        val chosen = if (count > 0) NotificationBadge.of(context.inkPrefs()) else NotificationBadge.NONE
        val badge = if (page == 0 || chosen == NotificationBadge.NONE) chosen else NotificationBadge.DOT
        val state = if (badge == NotificationBadge.NONE) null else resources.getQuantityString(R.plurals.notification_count, count, count)
        bar.show(page, layout.pageCount, hasPrev = page > 0 || movingId == null, hasNext = true, prevBadge = badge.label(count), prevState = state)
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
        grid.framed = null
        layout.on(sheet).forEach { placed ->
            val view = view(placed)
            grid.add(view, placed.spot.column, placed.spot.row, placed.spot.width, placed.spot.height)
            if (placed.tile is WidgetTile && placed.tile.id == movingId) grid.framed = view
        }
        grid.marked = movingId?.let { layout.dropSpots(it, sheet) }.orEmpty().map { CellGrid.Area(it.column, it.row, it.width, it.height) }
    }

    private fun view(placed: Placed): View = when (val tile = placed.tile) {
        is LabeledTile -> activity.tileView(tile).apply { isSelected = tile.id == movingId }.withMenu(placed)
        is WidgetTile -> activity.widgets.view(tile.widgetId)?.apply {
            monochrome = tile.monochrome
            onTapInstead = if (movingId == null) null else { { tapped(placed, pressBuzzed = false) } }
        } ?: activity.missingWidget(tile).withMenu(placed)
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

    private fun tapped(placed: Placed, pressBuzzed: Boolean = true) {
        val moving = movingId
        if (moving != null) {
            if (moving == placed.tile.id) return
            val dropped = layout.drop(moving, placed.tile.id, context.getString(R.string.folder_default_name))
                ?: return explainRefusedDrop(moving, placed.tile, vibrate = !pressBuzzed)
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
        val target = layout.dropSpots(moving, spot.sheet).firstOrNull { it.contains(spot.column, spot.row) }
            ?: layout.placementCovering(moving, spot.sheet, spot.column, spot.row)
            ?: return refuse(R.string.move_no_room, vibrate = true)
        endMove()
        activity.update { it.move(moving, target) }
        (layout.find(moving)?.spot?.sheet as? Sheet.Page)?.let { showPage(it.index) }
    }

    private fun explainRefusedDrop(moving: Long, target: Tile, vibrate: Boolean) {
        val intoFolder = target is AppTile || target is FolderTile
        val message = if (intoFolder && layout.find(moving)?.tile !is AppTile) R.string.move_only_apps_into_folders else R.string.move_no_room
        refuse(message, vibrate)
    }

    private fun refuse(@StringRes message: Int, vibrate: Boolean) {
        if (vibrate) Haptics.of(context).perform(HapticChannel.BUTTONS)
        bar.flashHint(context.getString(message))
    }
}
