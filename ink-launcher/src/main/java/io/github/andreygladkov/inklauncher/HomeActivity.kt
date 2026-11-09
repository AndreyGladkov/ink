package io.github.andreygladkov.inklauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inklauncher.apps.AppCatalog
import io.github.andreygladkov.inklauncher.home.AppKey
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.CustomIcon
import io.github.andreygladkov.inklauncher.home.FolderTile
import io.github.andreygladkov.inklauncher.home.HomeLayout
import io.github.andreygladkov.inklauncher.home.LabeledTile
import io.github.andreygladkov.inklauncher.home.LayoutStore
import io.github.andreygladkov.inklauncher.home.ScreenTimeTile
import io.github.andreygladkov.inklauncher.home.Sheet
import io.github.andreygladkov.inklauncher.home.Spot
import io.github.andreygladkov.inklauncher.home.Tile
import io.github.andreygladkov.inklauncher.home.WidgetTile
import io.github.andreygladkov.inklauncher.home.withIcon
import io.github.andreygladkov.inklauncher.icons.CustomPictures
import io.github.andreygladkov.inklauncher.icons.IconPack
import io.github.andreygladkov.inklauncher.icons.IconPackInfo
import io.github.andreygladkov.inklauncher.icons.IconRepository
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import io.github.andreygladkov.inklauncher.screentime.DayUsage
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeKeeper
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeText
import io.github.andreygladkov.inklauncher.screentime.UsagePeriod
import io.github.andreygladkov.inklauncher.settings.SettingsActivity
import io.github.andreygladkov.inklauncher.ui.ActionSheet
import io.github.andreygladkov.inklauncher.ui.AllAppsScreen
import io.github.andreygladkov.inklauncher.ui.FolderScreen
import io.github.andreygladkov.inklauncher.ui.HomeScreen
import io.github.andreygladkov.inklauncher.ui.NotificationsScreen
import io.github.andreygladkov.inklauncher.ui.PackIconsScreen
import io.github.andreygladkov.inklauncher.ui.ScreenTimeScreen
import io.github.andreygladkov.inklauncher.ui.ScreenTimeView
import io.github.andreygladkov.inklauncher.ui.SideScreen
import io.github.andreygladkov.inklauncher.ui.SwipeFrame
import io.github.andreygladkov.inklauncher.ui.TileMenus
import io.github.andreygladkov.inklauncher.ui.TileView
import io.github.andreygladkov.inklauncher.ui.WidgetPickerScreen
import io.github.andreygladkov.inklauncher.ui.keepAboveKeyboard
import io.github.andreygladkov.inklauncher.widgets.InkWidgetHost
import java.time.Clock

class HomeActivity : Activity() {
    lateinit var catalog: AppCatalog
        private set
    lateinit var icons: IconRepository
        private set
    lateinit var widgets: InkWidgetHost
        private set
    lateinit var home: HomeScreen
        private set
    lateinit var screenTime: ScreenTimeKeeper
        private set
    val menus = TileMenus(this)

    var layout = HomeLayout()
        private set

    private lateinit var style: InkStyle
    private lateinit var store: LayoutStore
    private lateinit var stage: FrameLayout
    private lateinit var homeFrame: View
    private var screen: View? = null
    private var side: SideScreen? = null
    private var sheet: ActionSheet? = null
    private var appearance = ""
    private var pendingWidget: Pair<Int, Spot>? = null
    private var pendingIconTile: Long? = null
    private var todayLog: UsagePeriod? = null
    private var today: DayUsage? = null

    @VisibleForTesting
    var clock: () -> Clock = Clock::systemDefaultZone

    private val onNotifications: () -> Unit = {
        home.updateBar()
        (side as? NotificationsScreen)?.refresh()
    }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            val removed = intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            catalog.invalidate()
            IconPack.forget(pkg)
            icons.forgetMemory()
            if (removed) {
                layout.widgetsOf(pkg).forEach { widgets.remove(it.widgetId) }
                update { it.withoutPackage(pkg) }
            } else {
                val before = layout
                update { it.relinked(launchable()) }
                if (layout === before) refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        style = InkStyle.of(this)
        catalog = AppCatalog(this)
        icons = IconRepository(this, catalog).apply {
            onPackLoaded = {
                forgetMemory()
                refresh()
            }
        }
        widgets = InkWidgetHost(this).apply { onLongPress = ::widgetLongPressed }
        screenTime = ScreenTimeKeeper(this, clock)
        store = LayoutStore(this)
        val stored = store.load()
        layout = (stored ?: firstLayout()).relinked(launchable())
        if (layout != stored) store.save(layout)
        appearance = appearance()
        home = HomeScreen(this)
        homeFrame = SwipeFrame(this) { towardsLeft -> if (towardsLeft) home.next() else home.prev() }.apply { addView(home) }
        stage = FrameLayout(this).apply { addView(homeFrame) }
        InkWindow.setContent(this, stage, padForKeyboard = false)
        home.bind(layout)
        savedInstanceState?.let(::restorePending)
        ContextCompat.registerReceiver(this, packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        NotificationFeed.observe(onNotifications)
    }

    override fun onDestroy() {
        NotificationFeed.stopObserving(onNotifications)
        unregisterReceiver(packages)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
        if (pendingWidget == null) forgetUnplacedWidgets()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    override fun onResume() {
        super.onResume()
        if (InkStyle.of(this) != style) return recreate()
        val stored = store.load() ?: layout
        val looks = appearance()
        if (stored != layout || looks != appearance) {
            layout = stored
            appearance = looks
            icons.forgetMemory()
            refresh()
        }
        home.updateBar()
        refreshScreenTime()
        (side as? ScreenTimeScreen)?.reload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Intent.ACTION_MAIN || !intent.hasCategory(Intent.CATEGORY_HOME)) return
        val wasInFront = hasWindowFocus()
        dismissSheet()
        home.cancelMove()
        if (screen != null) showHome() else if (wasInFront) home.showPage(0)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            sheet != null -> dismissSheet()
            side?.onBack() == true -> Unit
            screen != null -> showHome()
            home.cancelMove() -> Unit
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingWidget?.let { (id, spot) ->
            outState.putInt(STATE_WIDGET_ID, id)
            outState.putString(STATE_WIDGET_SHEET, sheetName(spot.sheet))
            outState.putIntArray(STATE_WIDGET_SPOT, intArrayOf((spot.sheet as? Sheet.Page)?.index ?: 0, spot.column, spot.row))
        }
        pendingIconTile?.let { outState.putLong(STATE_ICON_TILE, it) }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND_WIDGET -> if (resultCode == RESULT_OK) configureOrPlaceWidget() else cancelWidget()
            REQUEST_CONFIGURE_WIDGET -> if (resultCode == RESULT_OK) placeWidget() else cancelWidget()
            REQUEST_PICTURE -> {
                val tile = pendingIconTile.also { pendingIconTile = null } ?: return
                val uri = data?.data ?: return
                if (resultCode != RESULT_OK) return
                CustomPictures.import(this, uri)?.let { setIcon(tile, CustomIcon.Picture(it)) }
            }
        }
    }

    /**
     * Empty pages go after every change, except [keepPage]: the one on screen, counted in the changed layout.
     * The change is made to the stored layout, not the one in memory: settings write it while this activity is
     * stopped, and a package event can arrive before onResume picks their change up.
     */
    fun update(keepPage: Int = home.page, change: (HomeLayout) -> HomeLayout?) {
        val current = store.load() ?: layout
        val changed = change(current)
        val next = changed?.withoutEmptyPages(keep = keepPage) ?: current
        if (next == layout) return
        changed?.let { home.followPage(keepPage - it.emptyPagesBefore(keepPage)) }
        layout = next
        if (next != current) store.save(next)
        CustomPictures.keepOnly(this, pictures(next))
        refresh()
    }

    fun labelOf(tile: LabeledTile): String = when (tile) {
        is AppTile -> tile.label ?: catalog.label(tile.app)
        is FolderTile -> tile.name
    }

    fun tileView(tile: LabeledTile): TileView {
        val label = labelOf(tile)
        val shown = label.takeIf { inkPrefs().getBoolean(LauncherPrefs.LABELS, true) && !tile.labelHidden }
        return TileView(this, shown) { size -> icons.tile(tile, size, style, label) }.apply { contentDescription = label }
    }

    fun missingWidget(tile: WidgetTile): TileView =
        TileView(this, getString(R.string.widget_missing)) { size -> icons.text("?", size, style) }.apply { contentDescription = tile.provider.packageName }

    fun screenTimeView(): ScreenTimeView = ScreenTimeView(this).also(::showScreenTime)

    fun refreshScreenTime() {
        if (layout.placed.none { it.tile is ScreenTimeTile }) return
        todayLog = screenTime.period(1)
        retallyScreenTime()
    }

    private fun retallyScreenTime() {
        today = todayLog?.report(screenTime.categories())?.days?.single()
        home.screenTimeViews().forEach(::showScreenTime)
    }

    private fun showScreenTime(view: ScreenTimeView) {
        val usage = today ?: return view.showMessage(getString(R.string.screen_time_tap_for_access))
        val total = usage.drifting + usage.onPurpose
        view.showUsage(ScreenTimeView.Usage(
            drifting = ScreenTimeText.shortDuration(this, usage.drifting),
            onPurpose = ScreenTimeText.shortDuration(this, usage.onPurpose),
            driftingShare = if (total > 0) usage.drifting.toFloat() / total else 0f,
            unlocks = usage.unlocks,
        ))
    }

    fun openScreenTime() {
        if (!screenTime.isGranted) return screenTime.requestAccess()
        showScreen(ScreenTimeScreen(this)) {}
    }

    fun setCategory(packageName: String, category: AppCategory) {
        screenTime.setCategory(packageName, category)
        retallyScreenTime()
        side?.refresh()
    }

    fun launch(app: AppKey) {
        catalog.launch(app)
    }

    fun showHome() {
        dismissSheet()
        screen?.let(stage::removeView)
        screen = null
        side = null
        homeFrame.visibility = View.VISIBLE
    }

    fun showNotifications() = showScreen(NotificationsScreen(this)) { towardsLeft -> if (towardsLeft) showHome() }

    fun showAllApps(target: Spot? = null) = showScreen(AllAppsScreen(this, target)) { towardsLeft -> if (!towardsLeft) showHome() }

    fun openFolder(folderId: Long) {
        val folder = FolderScreen(this, folderId)
        showScreen(folder) { towardsLeft -> if (towardsLeft) folder.next() else folder.prev() }
    }

    fun showWidgetPicker(target: Spot) = showScreen(WidgetPickerScreen(this, target)) {}

    fun showPackIcons(pack: IconPackInfo, tileId: Long) = showScreen(PackIconsScreen(this, pack, tileId)) {}

    fun showSheet(sheet: ActionSheet) {
        this.sheet?.let(stage::removeView)
        this.sheet = sheet
        sheet.keepAboveKeyboard()
        stage.addView(sheet)
    }

    fun dismissSheet() {
        val current = sheet ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(current.windowToken, 0)
        stage.removeView(current)
        sheet = null
    }

    fun showKeyboard(field: View) {
        field.requestFocus()
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun placeApp(app: AppKey, target: Spot) {
        update { layout ->
            val tile = AppTile(layout.nextId(), app)
            if (layout.fits(target)) layout.place(tile, target) else layout.placeFirstFree(tile, fromPage = home.page)
        }
        showHome()
    }

    fun addToHome(app: AppKey) {
        val tile = AppTile(layout.nextId(), app)
        update { it.placeFirstFree(tile, fromPage = home.page) }
        showHome()
        (layout.find(tile.id)?.spot?.sheet as? Sheet.Page)?.let { home.showPage(it.index) }
    }

    fun setIcon(tileId: Long, icon: CustomIcon?) {
        dismissSheet()
        update { layout -> layout.changed<LabeledTile>(tileId) { it.withIcon(icon) } }
        if (side is PackIconsScreen) showHome()
    }

    fun pickPicture(tileId: Long) {
        dismissSheet()
        pendingIconTile = tileId
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICTURE)
    }

    fun openSettings() = startActivity(SettingsActivity.intent(this))

    fun addWidget(info: AppWidgetProviderInfo, target: Spot) {
        val id = widgets.allocateAppWidgetId()
        pendingWidget = id to target
        if (widgets.bindIfAllowed(id, info.provider)) {
            configureOrPlaceWidget()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(InkWidgetHost.bindIntent(id, info.provider), REQUEST_BIND_WIDGET)
        }
    }

    private fun configureOrPlaceWidget() {
        val (id, _) = pendingWidget ?: return
        val info = widgets.info(id) ?: return cancelWidget()
        if (info.configure == null) return placeWidget()
        try {
            widgets.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
        } catch (_: ActivityNotFoundException) {
            placeWidget()
        }
    }

    fun addScreenTime(target: Spot) {
        placeSized(ScreenTimeTile(layout.nextId()), target, ScreenTimeTile.COLUMNS.coerceAtMost(layout.spec.columns), ScreenTimeTile.ROWS)
        refreshScreenTime()
    }

    private fun placeWidget() {
        val (id, target) = pendingWidget ?: return
        pendingWidget = null
        val info = widgets.info(id) ?: return widgets.remove(id)
        val (cellWidth, cellHeight) = home.cellSize()
        val spec = layout.spec
        val width = InkWidgetHost.span(info.minWidth, cellWidth, spec.columns)
        val height = InkWidgetHost.span(info.minHeight, cellHeight, spec.rowsOf(target.sheet))
        placeSized(WidgetTile(layout.nextId(), id, InkWidgetHost.key(info)), target, width, height)
    }

    private fun placeSized(tile: Tile, target: Spot, width: Int, height: Int) {
        val rows = layout.spec.rowsOf(target.sheet)
        val shifted = target.copy(
            column = target.column.coerceAtMost(layout.spec.columns - width),
            row = target.row.coerceAtMost(rows - height).coerceAtLeast(0),
            width = width,
            height = height,
        )
        update { layout ->
            when {
                layout.fits(shifted) -> layout.place(tile, shifted)
                else -> layout.freeSpot(target.sheet, width, height)?.let { layout.place(tile, it) }
                    ?: layout.placeFirstFree(tile, width, height, fromPage = home.page)
            }
        }
        showHome()
        (layout.find(tile.id)?.spot?.sheet as? Sheet.Page)?.let { home.showPage(it.index) }
    }

    private fun cancelWidget() {
        val (id, _) = pendingWidget ?: return
        pendingWidget = null
        widgets.remove(id)
    }

    private fun forgetUnplacedWidgets() {
        val placed = layout.placed.mapNotNull { (it.tile as? WidgetTile)?.widgetId }.toSet()
        widgets.appWidgetIds.filter { it !in placed }.forEach(widgets::remove)
    }

    private fun widgetLongPressed(widgetId: Int) {
        if (home.movingId != null) return
        layout.placed.firstOrNull { (it.tile as? WidgetTile)?.widgetId == widgetId }?.let(menus::tile)
    }

    private fun <T> showScreen(view: T, onSwipe: (towardsLeft: Boolean) -> Unit) where T : View, T : SideScreen {
        dismissSheet()
        screen?.let(stage::removeView)
        side = view
        val frame = SwipeFrame(this, onSwipe).apply {
            setBackgroundColor(style.background)
            addView(view)
            keepAboveKeyboard()
        }
        screen = frame
        stage.addView(frame, 1)
        homeFrame.visibility = View.INVISIBLE
    }

    private fun refresh() {
        home.bind(layout)
        side?.refresh()
    }

    private fun appearance() = inkPrefs().let { prefs ->
        val pack = prefs.getString(LauncherPrefs.ICON_PACK, null)
        "${prefs.getBoolean(LauncherPrefs.LABELS, true)}:${prefs.getBoolean(LauncherPrefs.ICON_FRAME, false)}:$pack:" +
            "${pack?.let { prefs.getBoolean(LauncherPrefs.packInvertKey(it), false) }}"
    }

    private fun launchable() = catalog.apps().map { it.key }

    private fun firstLayout(): HomeLayout {
        var layout = HomeLayout()
        catalog.defaultDock().take(layout.spec.columns).forEachIndexed { column, app ->
            layout = layout.place(AppTile(layout.nextId(), app), Spot(Sheet.Bottom, column, 0))
        }
        return layout
    }

    private fun pictures(layout: HomeLayout): Set<String> = layout.placed.flatMap { placed ->
        when (val tile = placed.tile) {
            is AppTile -> listOf(tile.icon)
            is FolderTile -> listOf(tile.icon) + tile.apps.map { it.icon }
            is WidgetTile, is ScreenTimeTile -> emptyList()
        }
    }.mapNotNull { (it as? CustomIcon.Picture)?.file }.toSet()

    private fun restorePending(state: Bundle) {
        if (state.containsKey(STATE_ICON_TILE)) pendingIconTile = state.getLong(STATE_ICON_TILE)
        val spot = state.getIntArray(STATE_WIDGET_SPOT) ?: return
        val sheet = when (state.getString(STATE_WIDGET_SHEET)) {
            "top" -> Sheet.Top
            "bottom" -> Sheet.Bottom
            else -> Sheet.Page(spot[0])
        }
        pendingWidget = state.getInt(STATE_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) to Spot(sheet, spot[1], spot[2])
    }

    private fun sheetName(sheet: Sheet) = when (sheet) {
        Sheet.Top -> "top"
        Sheet.Bottom -> "bottom"
        is Sheet.Page -> "page"
    }

    private companion object {
        const val REQUEST_BIND_WIDGET = 1
        const val REQUEST_CONFIGURE_WIDGET = 2
        const val REQUEST_PICTURE = 3
        const val STATE_WIDGET_ID = "pending_widget_id"
        const val STATE_WIDGET_SHEET = "pending_widget_sheet"
        const val STATE_WIDGET_SPOT = "pending_widget_spot"
        const val STATE_ICON_TILE = "pending_icon_tile"
    }
}
