package io.github.andreygladkov.inklauncher

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Rect
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.DottedDrawable
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.home.AppKey
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.GridSpec
import io.github.andreygladkov.inklauncher.home.FolderTile
import io.github.andreygladkov.inklauncher.home.HomeLayout
import io.github.andreygladkov.inklauncher.home.LayoutStore
import io.github.andreygladkov.inklauncher.home.ScreenTimeTile
import io.github.andreygladkov.inklauncher.home.Sheet
import io.github.andreygladkov.inklauncher.home.Spot
import io.github.andreygladkov.inklauncher.notify.InkNotification
import io.github.andreygladkov.inklauncher.notify.InkNotificationListener
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import io.github.andreygladkov.inklauncher.settings.SettingsActivity
import io.github.andreygladkov.inklauncher.ui.ActionSheet
import io.github.andreygladkov.inklauncher.ui.AllAppsScreen
import io.github.andreygladkov.inklauncher.ui.AppRow
import io.github.andreygladkov.inklauncher.ui.CellGrid
import io.github.andreygladkov.inklauncher.ui.CategoryMark
import io.github.andreygladkov.inklauncher.ui.FolderScreen
import io.github.andreygladkov.inklauncher.ui.HomeScreen
import io.github.andreygladkov.inklauncher.ui.NotificationsScreen
import io.github.andreygladkov.inklauncher.ui.PagerBar
import io.github.andreygladkov.inklauncher.ui.ScreenTimeScreen
import io.github.andreygladkov.inklauncher.ui.ScreenTimeView
import io.github.andreygladkov.inklauncher.ui.SheetAction
import io.github.andreygladkov.inklauncher.ui.TileView
import io.github.andreygladkov.inklauncher.ui.WidgetPickerScreen
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class HomeActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = LayoutStore(context)
    private val mail = AppKey("com.mail", "com.mail.Inbox")
    private val chat = AppKey("com.chat", "com.chat.Main")
    private val maps = AppKey("org.maps", "org.maps.Map")
    private val reader = AppKey("org.koreader.launcher", "org.koreader.launcher.MainActivity")
    private val noon = LocalDate.of(2026, 11, 8).atTime(12, 0).atZone(ZoneId.systemDefault())
    private val now = noon.toInstant().toEpochMilli()

    @Before fun installApps() {
        install(mail, "Почта")
        install(chat, "Чат")
        install(maps, "Карты")
        install(reader, "Читалка")
    }

    @After fun clearNotifications() = NotificationFeed.publish(emptyList())

    @Test fun theFirstHomeIsEmptyAndPureBlackAndWhite() {
        val activity = open()
        assertTrue(tiles(activity).isEmpty())
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun tilesAreDrawnInBlackAndWhiteWithTheirLabels() {
        seed(HomeLayout()
            .place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0))
            .place(AppTile(2, chat, label = "Друзья"), Spot(Sheet.Bottom, 0, 0))
            .place(FolderTile(3, "Карты и почта", listOf(AppTile(4, maps), AppTile(5, mail))), Spot(Sheet.Top, 1, 1)))
        val activity = open()
        assertEquals(setOf("Почта", "Друзья", "Карты и почта"), tiles(activity).map { it.label }.toSet())
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aLongLabelIsCutShortAtTheSameSizeAsTheOthers() {
        seed(HomeLayout()
            .place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0))
            .place(AppTile(2, chat, label = "Очень длинное название приложения"), Spot(Sheet.Page(0), 1, 0)))
        val activity = open()
        render(contentRoot(activity))
        val short = tile(activity, "Почта")
        val long = tile(activity, "Очень длинное название приложения")
        assertEquals(short.labelTextSizePx, long.labelTextSizePx)
        assertEquals("Почта", short.shownLabel.toString())
        assertTrue(long.shownLabel.endsWith("…") && long.shownLabel.length < long.label!!.length, "${long.shownLabel}")
    }

    @Test fun thePageArrowsSitBelowTheDock() {
        val activity = open()
        render(contentRoot(activity))
        val dock = contentRoot(activity).find<CellGrid>().last()
        val arrow = arrow(activity, "Следующая страница")
        val (_, dockTop) = IntArray(2).also(dock::getLocationInWindow)
        val (_, arrowTop) = IntArray(2).also(arrow::getLocationInWindow)
        assertTrue(arrowTop >= dockTop + dock.height, "arrow at $arrowTop, dock ends at ${dockTop + dock.height}")
    }

    @Test fun homeArrowsLineUpWithTheWayBackFromTheSideScreens() {
        val activity = open()
        laidOut(activity)
        val homeNext = windowRect(arrow(activity, "Следующая страница"))
        val homePrev = windowRect(arrow(activity, "Предыдущая страница"))
        arrow(activity, "Предыдущая страница").performClick()
        laidOut(activity)
        val back = windowRect(homeButton(contentRoot(activity).find<NotificationsScreen>().single()))
        assertEquals(homeNext, back)
        activity.showAllApps()
        laidOut(activity)
        assertEquals(homePrev, windowRect(homeButton(contentRoot(activity).find<AllAppsScreen>().single())))
    }

    private fun windowRect(view: View): Rect {
        val (left, top) = IntArray(2).also(view::getLocationInWindow)
        return Rect(left, top, left + view.width, top + view.height)
    }

    @Test fun theLeftArrowOnTheFirstPageOpensNotificationsWithTheirCount() {
        NotificationFeed.publish(listOf(
            InkNotification("a", "com.mail", "Письмо", "Привет", 2),
            InkNotification("b", "com.mail", "Тихое", "", 1, isSilent = true),
        ))
        grantNotificationAccess()
        val activity = open()
        val prev = arrow(activity, "Предыдущая страница")
        assertEquals("1", prev.text.toString())
        prev.performClick()
        laidOut(activity)
        val screen = contentRoot(activity).find<NotificationsScreen>().single()
        val texts = screen.find<InkTextView>().map { it.text.toString() }
        assertTrue("Письмо" in texts && "Тихое" in texts, "$texts")
    }

    @Test fun theBadgeChosenInSettingsShowsOnReturningHomeWithoutMovingThePageDots() {
        grantNotificationAccess()
        NotificationFeed.publish(listOf(
            InkNotification("a", "com.mail", "Письмо", "Привет", 2),
            InkNotification("b", "com.chat", "Чат", "Как дела", 1),
        ))
        val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val activity = home.get()
        val prev = arrow(activity, "Предыдущая страница")
        assertEquals("2", prev.text.toString())
        assertEquals("2 уведомления", prev.stateDescription?.toString())
        val dotsWithNumber = pageDots(activity)

        home.pause()
        chooseBadge("Точка")
        home.resume()
        assertEquals("●", prev.text.toString())
        assertEquals("2 уведомления", prev.stateDescription?.toString())
        val inkWithDot = blackPixels(activity, prev)
        assertPureBlackAndWhite(render(contentRoot(activity)))
        assertEquals(dotsWithNumber, pageDots(activity))

        home.pause()
        chooseBadge("Нет")
        home.resume()
        assertEquals("", prev.text.toString())
        assertNull(prev.stateDescription)
        assertTrue(blackPixels(activity, prev) < inkWithDot)
        assertEquals(dotsWithNumber, pageDots(activity))
    }

    @Test fun anUnchangedBadgeDoesNotLayTheBarOutAgain() {
        NotificationFeed.publish(listOf(InkNotification("a", "com.mail", "Письмо", "Привет", 2)))
        val activity = open()
        laidOut(activity)
        val prev = arrow(activity, "Предыдущая страница")
        activity.home.updateBar()
        assertFalse(prev.isLayoutRequested)
    }

    @Test fun allAppsOpensTheLauncherSettingsButNotWhilePickingAnApp() {
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        val gear = contentRoot(activity).find<AllAppsScreen>().single().find<InkButton>().single { it.contentDescription == "Настройки" }
        gear.performClick()
        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        activity.showAllApps(target = Spot(Sheet.Page(0), 0, 0))
        assertTrue(contentRoot(activity).find<AllAppsScreen>().single().find<InkButton>().none { it.contentDescription == "Настройки" })
    }

    @Test fun theRightArrowOnTheLastPageOpensAllAppsInOrder() {
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        val screen = contentRoot(activity).find<AllAppsScreen>().single()
        laidOut(activity)
        val names = screen.find<InkButton>().map { it.text.toString() }.filter { it in setOf("Почта", "Чат", "Карты") }
        assertEquals(listOf("Карты", "Почта", "Чат"), names)
    }

    @Test fun thePageDotsOpenAllAppsFromAnyPage() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)).withPageAfter(0).place(AppTile(2, chat), Spot(Sheet.Page(1), 0, 0)))
        val activity = open()
        assertEquals(0, activity.home.page)
        contentRoot(activity).find<HomeScreen>().single().find<View>().single { it.contentDescription == "Все приложения" }.performClick()
        assertNotNull(contentRoot(activity).find<AllAppsScreen>().singleOrNull())
    }

    @Test fun theMagnifierFiltersAllAppsByNameUntilClosed() {
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        val screen = contentRoot(activity).find<AllAppsScreen>().single()
        screen.find<InkButton>().single { it.contentDescription == "Поиск по названию" }.performClick()
        assertTrue(screen.isSearching)
        screen.find<InkEditText>().single().setText("поч")
        laidOut(activity)
        assertEquals(listOf("Почта"), listed(screen))

        screen.find<InkEditText>().single().setText("ёлка")
        laidOut(activity)
        assertEquals(emptyList(), listed(screen))
        assertEquals(View.VISIBLE, screen.find<InkTextView>().single { it.text.toString() == "Ничего не найдено" }.visibility)

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        laidOut(activity)
        assertTrue(!screen.isSearching)
        assertEquals(listOf("Карты", "Почта", "Чат"), listed(screen))
        assertTrue(contentRoot(activity).find<AllAppsScreen>().isNotEmpty())
    }

    @Test fun widgetsAreFoundByTheirNameOrTheirAppsName() {
        addWidget(mail, "Входящие")
        addWidget(chat, "Последние")
        addWidget(maps, "Пробки")
        val activity = open()
        activity.showWidgetPicker(Spot(Sheet.Top, 0, 0))
        val screen = contentRoot(activity).find<WidgetPickerScreen>().single()
        laidOut(activity)
        assertEquals(listOf("Пробки", "Входящие", "Последние"), widgetsListed(screen))

        screen.find<InkButton>().single { it.contentDescription == "Поиск по виджету или приложению" }.performClick()
        screen.find<InkEditText>().single().setText("чат")
        laidOut(activity)
        assertEquals(listOf("Последние"), widgetsListed(screen))
        screen.find<InkEditText>().single().setText("вход")
        laidOut(activity)
        assertEquals(listOf("Входящие"), widgetsListed(screen))

        @Suppress("DEPRECATION")
        activity.onBackPressed()
        laidOut(activity)
        assertTrue(!screen.isSearching)
        assertEquals(3, widgetsListed(screen).size)
    }

    private fun addWidget(app: AppKey, label: String) {
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(app.packageName, "${app.packageName}.Widget") }
        // loadLabel reads providerInfo, a field the SDK stubs hide but the runtime has.
        AppWidgetProviderInfo::class.java.getField("providerInfo").set(info, ActivityInfo().apply {
            packageName = app.packageName
            name = "${app.packageName}.Widget"
            nonLocalizedLabel = label
            applicationInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
        })
        shadowOf(AppWidgetManager.getInstance(context)).addInstalledProvider(info)
    }

    private fun widgetsListed(screen: WidgetPickerScreen) =
        screen.find<InkTextView>().map { it.text.toString() }.filter { it in setOf("Входящие", "Последние", "Пробки") }

    private fun listed(screen: AllAppsScreen) =
        screen.find<InkButton>().map { it.text.toString() }.filter { it in setOf("Почта", "Чат", "Карты") }

    @Test fun everyShownAppInTheListHasItsIconInBlackAndWhite() {
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        laidOut(activity)
        val rows = contentRoot(activity).find<AllAppsScreen>().single().find<AppRow>()
        assertEquals(listOf("Карты", "Почта", "Чат"), rows.map { it.text.toString() }.filter { it in setOf("Почта", "Чат", "Карты") })
        assertTrue(rows.all { it.iconBitmap != null })
        assertTrue(rows.all { it.paddingLeft > it.iconBitmap!!.width })
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun anAppIsAddedFromTheListToTheFirstFreeCell() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        laidOut(activity)
        contentRoot(activity).find<InkButton>().first { it.text.toString() == "Чат" }.performLongClick()
        button(activity, "На главный экран").performClick()
        assertEquals(Spot(Sheet.Page(0), 1, 0), store.load()!!.placed.single { (it.tile as AppTile).app == chat }.spot)
        assertTrue(contentRoot(activity).find<AllAppsScreen>().isEmpty())
    }

    @Test fun aLongPressOnAnEmptyCellOffersAFolder() {
        val activity = open()
        render(contentRoot(activity))
        val grid = pagedGrid(activity)
        touch(grid, grid.width / 8f * 3, grid.height / 4f, holdMs = LONG_PRESS_MS)
        button(activity, "Папка").performClick()
        val folder = store.load()!!.placed.single()
        assertEquals(Spot(Sheet.Page(0), 1, 0), folder.spot)
        assertTrue(folder.tile is FolderTile)
        assertTrue(contentRoot(activity).find<ActionSheet>().isEmpty())
    }

    @Test fun aNewPageOpensEmptyAndGoesAwayWhenLeftEmpty() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        render(contentRoot(activity))
        val grid = pagedGrid(activity)
        touch(grid, grid.width / 8f * 7, grid.height / 4f * 3, holdMs = LONG_PRESS_MS)
        button(activity, "Новая страница").performClick()
        assertEquals(1, activity.home.page)
        assertEquals(2, store.load()!!.pageCount)
        assertTrue(tiles(activity).none { it.label == "Почта" })

        render(contentRoot(activity))
        touch(grid, grid.width / 8f, grid.height / 4f, holdMs = LONG_PRESS_MS)
        button(activity, "Новая страница").performClick()
        assertEquals(1, activity.home.page)
        assertEquals(2, store.load()!!.pageCount)

        arrow(activity, "Предыдущая страница").performClick()
        assertEquals(0, activity.home.page)
        assertEquals(1, store.load()!!.pageCount)
        assertNotNull(tiles(activity).singleOrNull { it.label == "Почта" })
    }

    @Test fun aNewPageKeepsWhatIsPutOnIt() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        activity.home.addPage()
        activity.placeApp(chat, Spot(Sheet.Page(1), 2, 1))
        arrow(activity, "Предыдущая страница").performClick()
        assertEquals(2, store.load()!!.pageCount)
        assertEquals(Spot(Sheet.Page(1), 2, 1), store.load()!!.placed.single { (it.tile as AppTile).app == chat }.spot)
    }

    @Test fun aTileMovesToTheCellTappedAfterMove() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        render(contentRoot(activity))
        tile(activity, "Почта").performLongClick()
        button(activity, "Переместить").performClick()
        val bottom = contentRoot(activity).find<CellGrid>().last()
        touch(bottom, bottom.width / 8f * 7, bottom.height / 2f)
        assertEquals(Spot(Sheet.Bottom, 3, 0), store.load()!!.find(1)!!.spot)
        assertNull(activity.home.movingId)
    }

    @Test fun anAppMovedOntoAnotherMakesAFolderThatOpens() {
        seed(HomeLayout()
            .place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0))
            .place(AppTile(2, chat), Spot(Sheet.Page(0), 1, 0)))
        val activity = open()
        render(contentRoot(activity))
        tile(activity, "Чат").performLongClick()
        button(activity, "Переместить").performClick()
        tile(activity, "Почта").performClick()
        val folder = store.load()!!.placed.single().tile as FolderTile
        assertEquals(listOf(mail, chat), folder.apps.map { it.app })
        tile(activity, "Папка").performClick()
        val screen = contentRoot(activity).find<FolderScreen>().single()
        assertEquals(listOf("Почта", "Чат"), screen.find<TileView>().map { it.label })
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aRenamedTileKeepsItsNewLabel() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        tile(activity, "Почта").performLongClick()
        button(activity, "Переименовать").performClick()
        val field = contentRoot(activity).find<InkEditText>().single()
        field.setText("Письма")
        button(activity, "Готово").performClick()
        assertEquals("Письма", (store.load()!!.find(1)!!.tile as AppTile).label)
        assertNotNull(tiles(activity).singleOrNull { it.label == "Письма" })
    }

    @Test fun theKeyboardLiftsTheSheetAndLeavesTheHomeGridAlone() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        val bars = Insets.of(0, 63, 0, 54)
        val keyboard = 700
        fun windowInsets(ime: Int) = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), bars)
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, ime))
            .build()
        tile(activity, "Почта").performLongClick()
        button(activity, "Переименовать").performClick()
        laidOut(activity)
        contentRoot(activity).dispatchApplyWindowInsets(windowInsets(0))
        render(contentRoot(activity))
        val before = tile(activity, "Почта").let { it.width to it.height }
        contentRoot(activity).dispatchApplyWindowInsets(windowInsets(keyboard))
        render(contentRoot(activity))
        assertEquals(before, tile(activity, "Почта").let { it.width to it.height })
        assertEquals(keyboard - 54, contentRoot(activity).find<ActionSheet>().single().paddingBottom)
    }

    @Test fun aGridChangedInSettingsSurvivesAnUninstallWhileAway() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)).place(AppTile(2, chat), Spot(Sheet.Page(0), 1, 0)))
        val activity = open()
        store.update { it.withSpec(GridSpec(columns = 5)) }
        shadowOf(context.packageManager).removeActivity(ComponentName(chat.packageName, chat.className))
        shadowOf(context.packageManager).removePackage(chat.packageName)
        activity.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED, android.net.Uri.fromParts("package", chat.packageName, null)).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(5, store.load()!!.spec.columns)
        assertEquals(listOf(1L), store.load()!!.placed.map { it.tile.id })
    }

    @Test fun aTileFollowsItsAppWhenAnUpdateMovesTheLauncherActivity() {
        seed(HomeLayout().place(AppTile(1, chat), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        val moved = AppKey(chat.packageName, "com.chat.NewMain")
        shadowOf(context.packageManager).removeActivity(ComponentName(chat.packageName, chat.className))
        install(moved, "Чат")
        activity.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REPLACED, android.net.Uri.fromParts("package", chat.packageName, null)).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(moved, (store.load()!!.find(1)!!.tile as AppTile).app)
    }

    @Test fun anUninstalledAppLeavesTheHomeScreen() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)).place(AppTile(2, chat), Spot(Sheet.Page(0), 1, 0)))
        val activity = open()
        shadowOf(context.packageManager).removeActivity(ComponentName(chat.packageName, chat.className))
        shadowOf(context.packageManager).removePackage(chat.packageName)
        activity.sendBroadcast(Intent(Intent.ACTION_PACKAGE_REMOVED, android.net.Uri.fromParts("package", chat.packageName, null)).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(1L), store.load()!!.placed.map { it.tile.id })
    }

    @Test fun backReturnsHomeFromASideScreen() {
        val activity = open()
        arrow(activity, "Следующая страница").performClick()
        @Suppress("DEPRECATION")
        activity.onBackPressed()
        assertTrue(contentRoot(activity).find<AllAppsScreen>().isEmpty())
        assertEquals(View.VISIBLE, contentRoot(activity).find<HomeScreen>().single().let { (it.parent as View).visibility })
    }

    @Test fun theBottomArrowsOfTheSideScreensLeadHome() {
        val activity = open()
        arrow(activity, "Предыдущая страница").performClick()
        homeButton(contentRoot(activity).find<NotificationsScreen>().single()).performClick()
        assertTrue(contentRoot(activity).find<NotificationsScreen>().isEmpty())

        arrow(activity, "Следующая страница").performClick()
        val apps = contentRoot(activity).find<AllAppsScreen>().single()
        laidOut(activity)
        val back = homeButton(apps)
        val (left, top) = IntArray(2).also(back::getLocationInWindow)
        assertTrue(top + back.height > apps.height * 9 / 10 && left < apps.width / 4, "$left,$top")
        back.performClick()
        assertTrue(contentRoot(activity).find<AllAppsScreen>().isEmpty())
    }

    @Test fun theScreenTimeTileAsksForUsageAccessUntilItIsGiven() {
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1)))
        usageAccess(AppOpsManager.MODE_DEFAULT)
        shadowOf(context).denyPermissions(Manifest.permission.PACKAGE_USAGE_STATS)
        val activity = open()
        val tile = screenTimeTile(activity)
        assertEquals("Нажмите, чтобы разрешить доступ к статистике", tile.message)
        assertPureBlackAndWhite(render(contentRoot(activity)))
        tile.performClick()
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test fun withoutAUsageAccessScreenTappingTheTileLeavesTheLauncherRunning() {
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1)))
        usageAccess(AppOpsManager.MODE_DEFAULT)
        shadowOf(context).denyPermissions(Manifest.permission.PACKAGE_USAGE_STATS)
        shadowOf(context).checkActivities(true)
        val activity = open()
        screenTimeTile(activity).performClick()
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(activity.isFinishing)
    }

    @Test fun theScreenTimeTileShowsTodayAndMovesAnAppsTimeWhenItIsMarked() {
        seed(HomeLayout()
            .place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1))
            .place(AppTile(2, reader), Spot(Sheet.Page(0), 2, 0)))
        grantUsageAccess()
        usage(reader.packageName, now - 50 * MINUTE, now - 10 * MINUTE)
        usage(maps.packageName, now - 10 * MINUTE, now - 5 * MINUTE)
        unlock(now - 55 * MINUTE)
        unlock(now - 30 * MINUTE)
        val activity = openAtNoon()
        assertEquals(ScreenTimeView.Usage("0 мин", "45 мин", 0f, 2), screenTimeTile(activity).usage)
        assertTrue(!screenTimeTile(activity).isCompact)
        assertPureBlackAndWhite(render(contentRoot(activity)))

        tile(activity, "Читалка").performLongClick()
        button(activity, "Экранное время: По делу").performClick()
        assertEquals(listOf(false, true, false), listOf("Залип", "По делу", "Не считать").map { (button(activity, it) as SheetAction).isChecked })
        val node = button(activity, "По делу").createAccessibilityNodeInfo()
        assertTrue(node.isCheckable && node.isChecked)
        button(activity, "Залип").performClick()
        val marked = screenTimeTile(activity).usage!!
        assertEquals(listOf("40 мин", "5 мин", "2"), listOf(marked.drifting, marked.onPurpose, marked.unlocks.toString()))
        assertEquals(40f / 45, marked.driftingShare, 0.001f)
        tile(activity, "Читалка").performLongClick()
        button(activity, "Экранное время: Залип").performClick()
        button(activity, "Не считать").performClick()
        assertEquals(ScreenTimeView.Usage("0 мин", "5 мин", 0f, 2), screenTimeTile(activity).usage)
    }

    @Test fun theLauncherItselfIsNotCountedUntilItIsChosen() {
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1)))
        grantUsageAccess()
        usage(context.packageName, now - 50 * MINUTE, now - 10 * MINUTE)
        usage(maps.packageName, now - 10 * MINUTE, now - 5 * MINUTE)
        val activity = openAtNoon()
        assertEquals(AppCategory.UNCOUNTED, activity.screenTime.category(context.packageName))
        assertEquals("5 мин", screenTimeTile(activity).usage!!.onPurpose)
        activity.setCategory(context.packageName, AppCategory.ON_PURPOSE)
        assertEquals("45 мин", screenTimeTile(activity).usage!!.onPurpose)
    }

    @Test fun aTileMenuListsItsActionsAsRowsBetweenDottedLinesAndItsArrowClosesIt() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        tile(activity, "Почта").performLongClick()
        val sheet = contentRoot(activity).find<ActionSheet>().single()
        val rows = sheet.find<SheetAction>()
        val lines = sheet.find<View>().filter { it.background is DottedDrawable }
        assertTrue(rows.size > 1 && rows.none { it.isBordered })
        assertEquals(rows.size - 1, lines.size)
        assertPureBlackAndWhite(render(contentRoot(activity)))
        val arrow = sheet.find<ActionSheet.CloseArrow>().single()
        arrow.isPressed = true
        assertPureBlackAndWhite(render(contentRoot(activity)))
        arrow.isPressed = false
        arrow.performClick()
        assertTrue(contentRoot(activity).find<ActionSheet>().isEmpty())
    }

    @Test fun anActionUnderAFieldKeepsAGapFromIt() {
        seed(HomeLayout().place(AppTile(1, mail), Spot(Sheet.Page(0), 0, 0)))
        val activity = open()
        tile(activity, "Почта").performLongClick()
        button(activity, "Переименовать").performClick()
        val done = button(activity, "Готово")
        assertTrue((done.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin > 0)
    }

    @Test fun choosingTheDefaultCategoryForgetsTheChoice() {
        val activity = open()
        activity.setCategory(reader.packageName, AppCategory.DRIFTING)
        activity.setCategory(reader.packageName, AppCategory.ON_PURPOSE)
        activity.setCategory(context.packageName, AppCategory.UNCOUNTED)
        assertEquals(emptySet(), activity.screenTime.categories().chosenApps)
        assertEquals(AppCategory.UNCOUNTED, activity.screenTime.category(context.packageName))
    }

    @Test fun theKeyboardInUseIsNotCountedAndTheTileFollowsASwitchOfKeyboards() {
        val vault = AppKey("org.vault", "org.vault.Main")
        install(vault, "Сейф")
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1)))
        grantUsageAccess()
        usage(vault.packageName, now - 50 * MINUTE, now - 10 * MINUTE)
        val activity = openAtNoon()
        assertEquals(AppCategory.ON_PURPOSE, activity.screenTime.category(vault.packageName))
        assertEquals("40 мин", screenTimeTile(activity).usage!!.onPurpose)

        Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, "org.vault/.Keyboard")
        context.contentResolver.notifyChange(Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AppCategory.UNCOUNTED, activity.screenTime.category(vault.packageName))
        assertEquals("0 мин", screenTimeTile(activity).usage!!.onPurpose)
    }

    @Test fun inOneCellTheScreenTimeTileStacksTheTimesWithoutNames() {
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0)))
        grantUsageAccess()
        usage(maps.packageName, now - 50 * MINUTE, now - 5 * MINUTE)
        val activity = openAtNoon()
        render(contentRoot(activity))
        val tile = screenTimeTile(activity)
        assertTrue(tile.isCompact)
        assertEquals("45 мин", tile.usage!!.onPurpose)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theWidgetListOffersScreenTimeFirstAndPutsItOnTwoCells() {
        addWidget(mail, "Входящие")
        val activity = open()
        activity.showWidgetPicker(Spot(Sheet.Page(0), 3, 1))
        laidOut(activity)
        val screen = contentRoot(activity).find<WidgetPickerScreen>().single()
        val names = screen.find<InkTextView>().map { it.text.toString() }.filter { it in setOf("Экранное время", "Входящие") }
        assertEquals(listOf("Экранное время", "Входящие"), names)
        (screen.find<InkTextView>().first { it.text.toString() == "Экранное время" }.parent as View).performClick()
        val placed = store.load()!!.placed.single()
        assertEquals(ScreenTimeTile(placed.tile.id), placed.tile)
        assertEquals(Spot(Sheet.Page(0), 2, 1, 2, 1), placed.spot)
        assertNotNull(screenTimeTile(activity))
    }

    @Test fun theScreenTimeScreenListsDaysAndAppsAndMarksAnApp() {
        seed(HomeLayout().place(ScreenTimeTile(1), Spot(Sheet.Page(0), 0, 0, 2, 1)))
        grantUsageAccess()
        usage(reader.packageName, now - 50 * MINUTE, now - 10 * MINUTE)
        usage(maps.packageName, now - 10 * MINUTE, now - 9 * MINUTE)
        usage(chat.packageName, now - 9 * MINUTE, now - 9 * MINUTE + 30_000)
        val activity = openAtNoon()
        screenTimeTile(activity).performClick()
        val screen = contentRoot(activity).find<ScreenTimeScreen>().single()
        laidOut(activity)
        val texts = screen.find<InkTextView>().map { it.text.toString() }
        assertTrue("Сегодня" in texts && "Вчера" in texts, "$texts")
        assertTrue("Залип 0 мин · По делу 41 мин\n0 разблокировок" in texts, "$texts")

        screen.find<InkButton>().single { it.text.toString() == "По приложениям" }.performClick()
        laidOut(activity)
        val apps = screen.find<InkTextView>().map { it.text.toString() }
        assertEquals(listOf("Читалка", "40 мин", "Карты", "1 мин"), apps.filter { it in setOf("Читалка", "Карты", "Чат", "40 мин", "1 мин") })
        assertEquals(listOf(AppCategory.ON_PURPOSE, AppCategory.ON_PURPOSE), screen.find<CategoryMark>().map { it.category })
        (screen.find<CategoryMark>().first().parent as View).performClick()
        button(activity, "Залип").performClick()
        laidOut(activity)
        assertEquals(AppCategory.DRIFTING, activity.screenTime.category(reader.packageName))
        assertEquals(listOf(AppCategory.DRIFTING, AppCategory.ON_PURPOSE), screen.find<CategoryMark>().map { it.category })
        assertEquals("40 мин", screenTimeTile(activity).usage!!.drifting)

        (screen.find<CategoryMark>()[1].parent as View).performClick()
        button(activity, "Не считать").performClick()
        laidOut(activity)
        assertTrue("1 мин · Не считать" in screen.find<InkTextView>().map { it.text.toString() })
        assertEquals("0 мин", screenTimeTile(activity).usage!!.onPurpose)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    private fun grantUsageAccess() = usageAccess(AppOpsManager.MODE_ALLOWED)

    private fun usageAccess(mode: Int) = shadowOf(context.getSystemService(AppOpsManager::class.java))
        .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)

    private fun usage(packageName: String, from: Long, to: Long) {
        val stats = shadowOf(context.getSystemService(UsageStatsManager::class.java))
        stats.addEvent(packageName, from, UsageEvents.Event.ACTIVITY_RESUMED)
        stats.addEvent(packageName, to, UsageEvents.Event.ACTIVITY_PAUSED)
    }

    private fun unlock(at: Long) =
        shadowOf(context.getSystemService(UsageStatsManager::class.java)).addEvent("android", at, UsageEvents.Event.KEYGUARD_HIDDEN)

    private fun screenTimeTile(activity: HomeActivity) = contentRoot(activity).find<ScreenTimeView>().single()

    private fun homeButton(screen: View) = screen.find<InkButton>().single { it.contentDescription == "Главный экран" }

    private fun install(key: AppKey, label: String) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = key.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = key.packageName
                nonLocalizedLabel = label
            }
        })
        val component = ComponentName(key.packageName, key.className)
        pm.addActivityIfNotPresent(component).nonLocalizedLabel = label
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private fun seed(layout: HomeLayout) = store.save(layout)

    private fun laidOut(activity: HomeActivity) {
        render(contentRoot(activity))
        shadowOf(Looper.getMainLooper()).idle()
        render(contentRoot(activity))
    }

    private fun open(): HomeActivity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

    private fun grantNotificationAccess() = shadowOf(context.getSystemService(NotificationManager::class.java))
        .setNotificationListenerAccessGranted(ComponentName(context, InkNotificationListener::class.java), true)

    private fun chooseBadge(label: String) {
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        contentRoot(settings.get()).find<InkButton>().single { it.text.toString() == label }.performClick()
        settings.pause().stop().destroy()
    }

    private fun blackPixels(activity: HomeActivity, view: View): Int {
        val bitmap = render(contentRoot(activity))
        val bounds = Rect(0, 0, view.width, view.height).also { contentRoot(activity).offsetDescendantRectToMyCoords(view, it) }
        return (bounds.left until bounds.right).sumOf { x -> (bounds.top until bounds.bottom).count { y -> bitmap.getPixel(x, y) == Color.BLACK } }
    }

    private fun pageDots(activity: HomeActivity): List<Int> {
        val bitmap = render(contentRoot(activity))
        val bar = contentRoot(activity).find<PagerBar>().single()
        val bounds = Rect(bar.width / 4, 0, bar.width * 3 / 4, bar.height).also { contentRoot(activity).offsetDescendantRectToMyCoords(bar, it) }
        return (bounds.top until bounds.bottom).flatMap { y -> (bounds.left until bounds.right).map { x -> bitmap.getPixel(x, y) } }
            .also { assertTrue(Color.BLACK in it) }
    }

    private fun openAtNoon(): HomeActivity = Robolectric.buildActivity(HomeActivity::class.java)
        .apply { get().clock = { Clock.fixed(noon.toInstant(), noon.zone) } }
        .setup().get()

    private fun tiles(activity: HomeActivity) = contentRoot(activity).find<TileView>()

    private fun tile(activity: HomeActivity, label: String) = tiles(activity).single { it.label == label }

    private fun arrow(activity: HomeActivity, description: String) =
        contentRoot(activity).find<HomeScreen>().single().find<InkButton>().single { it.contentDescription == description }

    private fun button(activity: HomeActivity, text: String) =
        contentRoot(activity).find<ActionSheet>().single().find<InkButton>().single { it.text.toString() == text }

    private fun pagedGrid(activity: HomeActivity) = contentRoot(activity).find<CellGrid>()[1]

    private fun touch(view: View, x: Float, y: Float, holdMs: Long = 0) {
        view.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(holdMs))
        view.dispatchTouchEvent(MotionEvent.obtain(0, holdMs, MotionEvent.ACTION_UP, x, y, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val LONG_PRESS_MS = 1000L
        const val MINUTE = 60_000L
    }
}
