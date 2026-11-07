package io.github.andreygladkov.inklauncher.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.CustomIcon
import io.github.andreygladkov.inklauncher.home.LabeledTile
import io.github.andreygladkov.inklauncher.icons.IconPack
import io.github.andreygladkov.inklauncher.icons.IconPackInfo
import io.github.andreygladkov.inklauncher.icons.PackGroup
import io.github.andreygladkov.inklauncher.icons.PackIcon
import io.github.andreygladkov.inklauncher.icons.PackIndex
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Picks a pack icon for a tile: the pack's icons for this app first, then groups to open, or a search over all. */
class PackIconsScreen(private val activity: HomeActivity, private val pack: IconPackInfo, private val tileId: Long) : LinearLayout(activity), SideScreen {
    private sealed interface Row {
        data class ForApp(val icons: List<PackIcon>) : Row
        data class Group(val group: PackGroup) : Row
    }

    private val tile: LabeledTile? = activity.layout.find(tileId)?.tile as? LabeledTile ?: activity.layout.app(tileId)
    private val padding = (PADDING_DP * resources.displayMetrics.density).roundToInt()
    private val search = SearchHeader(activity, pack.label, R.string.search_icons,
        headerButton(activity, R.drawable.ic_arrow_back, R.string.back) { back() }) { showQuery() }
    private val overview = InkPagedList(activity)
    private val grid = IconGrid(activity, pack.packageName, ::pick).apply { visibility = GONE }
    private val empty = nothingFound(activity)
    private val loading = InkTextView(activity).apply {
        setText(R.string.loading)
        gravity = Gravity.CENTER
    }
    private val loader = Executors.newSingleThreadExecutor()
    private var index: PackIndex? = null
    private var group: PackGroup? = null
    private var rows: List<Row> = emptyList()

    init {
        orientation = VERTICAL
        addView(search)
        addView(FrameLayout(activity).apply {
            addView(overview)
            addView(grid)
            listOf(empty, loading).forEach {
                addView(it, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val apps = activity.catalog.apps()
        val app = (tile as? AppTile)?.app
        loader.execute {
            val loaded = IconPack.load(activity, pack.packageName)
                ?.let { PackIndex.build(it.components, it.categories, apps, app) }
                ?: PackIndex(emptyList(), emptyList(), emptyList(), emptyList())
            post {
                index = loaded
                loading.visibility = GONE
                showOverview()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loader.shutdownNow()
    }

    override fun onBack(): Boolean {
        if (search.close()) return true
        if (group == null) return false
        showOverview()
        return true
    }

    private fun back() {
        if (group != null) showOverview() else activity.showHome()
    }

    private fun showOverview() {
        val index = index ?: return
        group = null
        search.setTitle(pack.label)
        val groups = listOfNotNull(index.installed.takeIf { it.isNotEmpty() }?.let { PackGroup(activity.getString(R.string.pack_installed), it) }) +
            index.categories.ifEmpty { listOf(PackGroup(activity.getString(R.string.pack_all), index.all)) }
        rows = listOfNotNull(index.forApp.takeIf { it.isNotEmpty() }?.let(Row::ForApp)) + groups.map(Row::Group)
        overview.setAdapter(adapter())
        overview.visibility = VISIBLE
        grid.visibility = GONE
        empty.visibility = GONE
    }

    private fun open(group: PackGroup) {
        this.group = group
        search.setTitle(group.title)
        showIcons(group.icons)
    }

    private fun showQuery() {
        val index = index ?: return
        val query = search.query
        val opened = group
        when {
            !query.isEmpty -> showIcons(index.search(query))
            opened != null -> open(opened)
            else -> showOverview()
        }
    }

    private fun showIcons(icons: List<PackIcon>) {
        grid.show(icons)
        overview.visibility = GONE
        grid.visibility = VISIBLE
        empty.visibility = if (icons.isEmpty()) VISIBLE else GONE
    }

    private fun pick(icon: PackIcon) {
        search.hideKeyboard()
        activity.setIcon(tileId, CustomIcon.FromPack(pack.packageName, icon.drawable))
    }

    private fun adapter() = object : InkPagedList.Adapter {
        override val count = rows.size
        override fun itemId(position: Int) = rows[position].hashCode().toLong()
        override fun createView(position: Int): View = when (val row = rows[position]) {
            is Row.ForApp -> forApp(row.icons)
            is Row.Group -> InkButton(activity).apply {
                text = activity.getString(R.string.pack_group, row.group.title, row.group.icons.size)
                isBordered = false
                maxLines = 1
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { open(row.group) }
            }
        }
    }

    private fun forApp(icons: List<PackIcon>) = LinearLayout(activity).apply {
        orientation = VERTICAL
        setPadding(padding, padding, padding, padding / 2)
        addView(InkTextView(activity).apply {
            text = activity.getString(R.string.pack_for_app, tile?.let(activity::labelOf).orEmpty())
            textScale = InkStyle.SECONDARY_SCALE
            isBold = true
        })
        val rows = (icons.size + FOR_APP_COLUMNS - 1) / FOR_APP_COLUMNS
        val cellPx = activity.home.cellSize().first
        addView(CellGrid(activity, FOR_APP_COLUMNS, rows).apply {
            icons.forEachIndexed { position, icon ->
                add(TileView(activity, null) { size ->
                    activity.icons.packIcon(pack.packageName, icon.drawable, size) ?: activity.icons.text("?", size, InkStyle.of(activity))
                }.apply {
                    contentDescription = icon.drawable
                    setOnClickListener { pick(icon) }
                }, position % FOR_APP_COLUMNS, position / FOR_APP_COLUMNS)
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, rows * cellPx))
    }

    private companion object {
        const val PADDING_DP = 16f
        const val FOR_APP_COLUMNS = 4
    }
}
