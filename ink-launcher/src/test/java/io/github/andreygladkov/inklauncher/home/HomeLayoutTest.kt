package io.github.andreygladkov.inklauncher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLayoutTest {
    private val spec = GridSpec(columns = 4, rows = 7, pinnedTop = 4, pinnedBottom = 1)

    private fun app(id: Long, name: String = "app$id") = AppTile(id, AppKey("pkg.$name", "pkg.$name.Main"))

    private fun page(index: Int) = Sheet.Page(index)

    @Test fun tilesFillThePageRowByRowAndOverflowToANewPage() {
        var layout = HomeLayout(spec)
        (1L..9L).forEach { layout = layout.placeFirstFree(app(it)) }
        assertEquals(2, layout.pageCount)
        assertEquals(Spot(page(0), 3, 1), layout.find(8)!!.spot)
        assertEquals(Spot(page(1), 0, 0), layout.find(9)!!.spot)
    }

    @Test fun aSpotIsFreeOnlyInsideItsSheetAndWithoutOverlap() {
        val layout = HomeLayout(spec).place(WidgetTile(1, 7, AppKey("w", "w.Clock")), Spot(Sheet.Top, 0, 0, 4, 2))
        assertFalse(layout.fits(Spot(Sheet.Top, 3, 1)))
        assertTrue(layout.fits(Spot(Sheet.Top, 3, 2)))
        assertFalse(layout.fits(Spot(Sheet.Top, 0, 3, 1, 2)))
        assertFalse(layout.fits(Spot(Sheet.Bottom, 0, 1)))
        assertFalse(layout.fits(Spot(page(1), 0, 0)))
        assertTrue(layout.fits(Spot(Sheet.Top, 0, 0, 4, 2), ignoring = 1))
    }

    @Test fun aPageLeftEmptyGoesAwayAndTheLaterOnesMoveUp() {
        val moved = HomeLayout(spec, pageCount = 3)
            .place(app(1), Spot(page(0), 0, 0))
            .place(app(2), Spot(page(1), 0, 0))
            .place(app(3), Spot(page(2), 0, 0))
            .move(2, Spot(page(0), 1, 0))
        assertEquals(1, moved.emptyPagesBefore(2))
        val trimmed = moved.withoutEmptyPages()
        assertEquals(2, trimmed.pageCount)
        assertEquals(Spot(page(1), 0, 0), trimmed.find(3)!!.spot)
    }

    @Test fun aNewPageGoesAfterTheGivenOneAndStaysWhileKept() {
        val layout = HomeLayout(spec, pageCount = 2)
            .place(app(1), Spot(page(0), 0, 0))
            .place(app(2), Spot(page(1), 0, 0))
            .withPageAfter(0)
        assertEquals(3, layout.pageCount)
        assertEquals(Spot(page(2), 0, 0), layout.find(2)!!.spot)
        assertEquals(layout, layout.withoutEmptyPages(keep = 1))
        assertEquals(2, layout.withoutEmptyPages(keep = 0).pageCount)
    }

    @Test fun anAppDroppedOnAnAppMakesAFolderInTheTargetsPlace() {
        val layout = HomeLayout(spec)
            .place(app(1), Spot(Sheet.Bottom, 2, 0))
            .place(app(2), Spot(page(0), 0, 0))
            .drop(movingId = 2, targetId = 1, newFolderName = "Folder")!!
        val folder = layout.find(3)!!
        assertEquals(Spot(Sheet.Bottom, 2, 0), folder.spot)
        assertEquals(listOf(1L, 2L), (folder.tile as FolderTile).apps.map { it.id })
        assertEquals(1, layout.placed.size)
    }

    @Test fun anAppPutIntoANewFolderLeavesTheFolderInItsPlace() {
        val layout = HomeLayout(spec).place(app(1), Spot(page(0), 2, 1)).place(app(2), Spot(page(0), 0, 0))
        val folded = layout.intoNewFolder(1, "Folder")!!
        assertEquals(FolderTile(3, "Folder", listOf(app(1))), folded.find(3)!!.tile)
        assertEquals(Spot(page(0), 2, 1), folded.find(3)!!.spot)
        assertEquals(2, folded.placed.size)
    }

    @Test fun anAppDroppedOnAFolderJoinsIt() {
        val layout = HomeLayout(spec)
            .place(FolderTile(1, "F", listOf(app(2))), Spot(page(0), 0, 0))
            .place(app(3), Spot(page(0), 1, 0))
            .drop(3, 1, "unused")!!
        assertEquals(listOf(2L, 3L), (layout.find(1)!!.tile as FolderTile).apps.map { it.id })
        assertNull(layout.find(3))
    }

    @Test fun neitherWidgetsNorFoldersCanBeDropped() {
        val layout = HomeLayout(spec)
            .place(WidgetTile(1, 5, AppKey("w", "w.W")), Spot(page(0), 0, 0))
            .place(FolderTile(2, "F"), Spot(page(0), 1, 0))
            .place(app(3), Spot(page(0), 2, 0))
        assertNull(layout.drop(1, 3, "F"))
        assertNull(layout.drop(2, 3, "F"))
        assertNull(layout.drop(3, 1, "F"))
    }

    @Test fun anAppTakenOutOfAFolderGoesToTheFirstFreeCellFromTheFoldersPage() {
        val layout = HomeLayout(spec, pageCount = 2)
            .place(app(9), Spot(page(0), 0, 0))
            .place(FolderTile(1, "F", listOf(app(2), app(3))), Spot(page(1), 0, 0))
            .takeOut(3)!!
        assertEquals(Spot(page(1), 1, 0), layout.find(3)!!.spot)
        assertEquals(listOf(2L), (layout.find(1)!!.tile as FolderTile).apps.map { it.id })
    }

    @Test fun renamingAnAppInsideAFolderKeepsItThere() {
        val layout = HomeLayout(spec).place(FolderTile(1, "F", listOf(app(2))), Spot(page(0), 0, 0))
        val renamed = layout.replace(app(2).copy(label = "Mail"))
        assertEquals("Mail", renamed.app(2)!!.label)
    }

    @Test fun aChangeAppliesToTheTileAsItIsNowNotAsItWasWhenTheMenuOpened() {
        val opened = HomeLayout(spec).place(FolderTile(1, "Folder", listOf(app(2), app(3))), Spot(page(0), 0, 0))
        val now = opened.withoutPackage("pkg.app3")
        val renamed = now.changed<LabeledTile>(1) { it.renamed("Work") }!!
        assertEquals(FolderTile(1, "Work", listOf(app(2))), renamed.find(1)!!.tile)
        assertEquals("Mail", (renamed.changed<LabeledTile>(2) { it.renamed("Mail") }!!.app(2))!!.label)
        assertNull(now.changed<LabeledTile>(3) { it.renamed("Gone") })
    }

    @Test fun aWidgetIsChangedOnlyAsAWidget() {
        val layout = HomeLayout(spec).place(WidgetTile(1, 5, AppKey("w", "w.W")), Spot(page(0), 0, 0)).place(app(2), Spot(page(0), 1, 0))
        assertEquals(WidgetTile(1, 5, AppKey("w", "w.W"), monochrome = true), layout.changed<WidgetTile>(1) { it.copy(monochrome = true) }!!.find(1)!!.tile)
        assertNull(layout.changed<WidgetTile>(2) { it.copy(monochrome = true) })
        assertNull(layout.changed<LabeledTile>(1) { it.renamed("Clock") })
    }

    @Test fun tilesFollowTheirAppToItsNewLauncherActivity() {
        val moved = AppKey("pkg.app1", "pkg.app1.NewMain")
        val layout = HomeLayout(spec)
            .place(app(1), Spot(page(0), 0, 0))
            .place(FolderTile(2, "Folder", listOf(app(3, "app1"), app(4))), Spot(page(0), 1, 0))
            .place(app(5), Spot(page(0), 2, 0))
        val relinked = layout.relinked(listOf(moved, app(4).app))
        assertEquals(moved, (relinked.find(1)!!.tile as AppTile).app)
        assertEquals(listOf(moved, app(4).app), (relinked.find(2)!!.tile as FolderTile).apps.map { it.app })
        assertEquals(app(5).app, (relinked.find(5)!!.tile as AppTile).app)
    }

    @Test fun anUninstalledPackageLeavesTheGridAndItsFolders() {
        val layout = HomeLayout(spec, pageCount = 2)
            .place(app(1, "gone"), Spot(page(1), 0, 0))
            .place(FolderTile(2, "F", listOf(app(3, "gone"), app(4, "kept"))), Spot(page(0), 0, 0))
            .place(WidgetTile(5, 11, AppKey("pkg.gone", "pkg.gone.Widget")), Spot(Sheet.Top, 0, 0, 2, 2))
        assertEquals(listOf(5L), layout.widgetsOf("pkg.gone").map { it.id })
        val left = layout.withoutPackage("pkg.gone").withoutEmptyPages()
        assertEquals(1, left.pageCount)
        assertEquals(listOf(2L), left.placed.map { it.tile.id })
        assertEquals(listOf(4L), (left.find(2)!!.tile as FolderTile).apps.map { it.id })
    }

    @Test fun aWidgetGrowsOnlyIntoFreeCells() {
        val layout = HomeLayout(spec)
            .place(WidgetTile(1, 5, AppKey("w", "w.W")), Spot(Sheet.Top, 0, 0, 2, 2))
            .place(app(2), Spot(Sheet.Top, 2, 0))
        assertNull(layout.resized(1, 3, 2))
        assertEquals(Spot(Sheet.Top, 0, 0, 2, 3), layout.resized(1, 2, 3)!!.find(1)!!.spot)
    }

    @Test fun aSmallerGridMovesWhatNoLongerFitsAfterTheLastPageAndDropsNothing() {
        val layout = HomeLayout(spec)
            .place(WidgetTile(1, 5, AppKey("w", "w.W")), Spot(Sheet.Top, 0, 0, 4, 2))
            .place(app(2), Spot(Sheet.Top, 0, 3))
            .place(app(3), Spot(page(0), 3, 1))
            .place(app(4), Spot(Sheet.Bottom, 3, 0))
        val smaller = layout.withSpec(GridSpec(columns = 3, rows = 6, pinnedTop = 3, pinnedBottom = 0))
        assertEquals(Spot(Sheet.Top, 0, 0, 3, 2), smaller.find(1)!!.spot)
        assertEquals(Spot(page(0), 2, 1), smaller.find(3)!!.spot)
        assertEquals(listOf(Spot(page(0), 0, 0), Spot(page(0), 1, 0)), listOf(2L, 4L).map { smaller.find(it)!!.spot })
        assertEquals(4, smaller.placed.size)
    }

    @Test fun aWidgetTallerThanThePagedRowsIsShrunkWhenItOverflows() {
        val layout = HomeLayout(spec).place(WidgetTile(1, 5, AppKey("w", "w.W")), Spot(Sheet.Top, 0, 0, 4, 4))
        val shrunk = layout.withSpec(spec.copy(pinnedTop = 0, rows = 3, pinnedBottom = 0))
        assertEquals(Spot(page(0), 0, 0, 4, 3), shrunk.find(1)!!.spot)
    }

    @Test fun idsAreUniqueAcrossFolders() {
        val layout = HomeLayout(spec).place(FolderTile(1, "F", listOf(app(7))), Spot(page(0), 0, 0))
        assertEquals(8, layout.nextId())
    }
}
