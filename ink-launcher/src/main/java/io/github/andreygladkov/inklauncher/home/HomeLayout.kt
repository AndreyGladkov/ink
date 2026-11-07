package io.github.andreygladkov.inklauncher.home

data class HomeLayout(
    val spec: GridSpec = GridSpec(),
    val pageCount: Int = 1,
    val placed: List<Placed> = emptyList(),
) {
    init {
        require(pageCount >= 1)
    }

    fun on(sheet: Sheet): List<Placed> = placed.filter { it.spot.sheet == sheet }

    fun find(id: Long): Placed? = placed.firstOrNull { it.tile.id == id }

    fun folders(): List<FolderTile> = placed.mapNotNull { it.tile as? FolderTile }

    fun folderOf(appId: Long): FolderTile? = folders().firstOrNull { folder -> folder.apps.any { it.id == appId } }

    fun app(id: Long): AppTile? = find(id)?.tile as? AppTile ?: folderOf(id)?.apps?.first { it.id == id }

    fun at(sheet: Sheet, column: Int, row: Int): Placed? = placed.firstOrNull { it.spot.sheet == sheet && it.spot.contains(column, row) }

    fun fits(spot: Spot, ignoring: Long? = null): Boolean {
        if (spot.column < 0 || spot.row < 0 || spot.width < 1 || spot.height < 1) return false
        if (spot.column + spot.width > spec.columns || spot.row + spot.height > spec.rowsOf(spot.sheet)) return false
        if (spot.sheet is Sheet.Page && spot.sheet.index !in 0 until pageCount) return false
        return placed.none { it.tile.id != ignoring && it.spot.overlaps(spot) }
    }

    fun nextId(): Long = (placed.flatMap { placed -> listOf(placed.tile.id) + ((placed.tile as? FolderTile)?.apps?.map { it.id } ?: emptyList()) }
        .maxOrNull() ?: 0) + 1

    fun freeSpot(sheet: Sheet, width: Int = 1, height: Int = 1): Spot? {
        for (row in 0..spec.rowsOf(sheet) - height) {
            for (column in 0..spec.columns - width) {
                val spot = Spot(sheet, column, row, width, height)
                if (fits(spot)) return spot
            }
        }
        return null
    }

    fun place(tile: Tile, spot: Spot): HomeLayout {
        require(fits(spot)) { "$spot is taken or outside the grid" }
        return copy(placed = placed + Placed(tile, spot))
    }

    /** Pages from [fromPage] on are filled first; a new page is added when none has room. */
    fun placeFirstFree(tile: Tile, width: Int = 1, height: Int = 1, fromPage: Int = 0): HomeLayout {
        val w = width.coerceAtMost(spec.columns)
        val h = height.coerceAtMost(spec.pagedRows)
        for (page in fromPage.coerceIn(0, pageCount - 1) until pageCount) {
            freeSpot(Sheet.Page(page), w, h)?.let { return place(tile, it) }
        }
        return copy(pageCount = pageCount + 1).place(tile, Spot(Sheet.Page(pageCount), 0, 0, w, h))
    }

    fun withPageAfter(index: Int): HomeLayout = copy(pageCount = pageCount + 1, placed = placed.map { placed ->
        val sheet = placed.spot.sheet
        if (sheet is Sheet.Page && sheet.index > index) placed.copy(spot = placed.spot.copy(sheet = Sheet.Page(sheet.index + 1))) else placed
    })

    fun move(id: Long, spot: Spot): HomeLayout {
        require(find(id) != null && fits(spot, ignoring = id)) { "cannot move $id to $spot" }
        return copy(placed = placed.map { if (it.tile.id == id) Placed(it.tile, spot) else it })
    }

    fun resized(id: Long, width: Int, height: Int): HomeLayout? {
        val current = find(id) ?: return null
        val spot = current.spot.copy(width = width, height = height)
        if (!fits(spot, ignoring = id)) return null
        return copy(placed = placed.map { if (it.tile.id == id) Placed(it.tile, spot) else it })
    }

    fun replace(tile: Tile): HomeLayout = copy(placed = placed.map { placed ->
        when {
            placed.tile.id == tile.id -> Placed(tile, placed.spot)
            tile is AppTile && placed.tile is FolderTile && placed.tile.apps.any { it.id == tile.id } ->
                Placed(placed.tile.copy(apps = placed.tile.apps.map { if (it.id == tile.id) tile else it }), placed.spot)
            else -> placed
        }
    })

    /** Changes the tile as it is now, so a menu opened before another change does not undo it. */
    fun changed(id: Long, change: (LabeledTile) -> LabeledTile): HomeLayout? {
        val current = find(id)?.tile as? LabeledTile ?: app(id) ?: return null
        return replace(change(current))
    }

    /** An update can move an app's launcher activity; its tiles follow it to the one the package has now. */
    fun relinked(launchable: Collection<AppKey>): HomeLayout {
        val byPackage = launchable.groupBy { it.packageName }
        fun relink(tile: AppTile): AppTile {
            if (tile.app in launchable) return tile
            val now = byPackage[tile.app.packageName]?.firstOrNull() ?: return tile
            return tile.copy(app = now)
        }
        return copy(placed = placed.map { placed ->
            when (val tile = placed.tile) {
                is AppTile -> placed.copy(tile = relink(tile))
                is FolderTile -> placed.copy(tile = tile.copy(apps = tile.apps.map(::relink)))
                is WidgetTile -> placed
            }
        })
    }

    fun remove(id: Long): HomeLayout {
        val folder = folderOf(id)
        if (folder != null) return replace(folder.copy(apps = folder.apps.filter { it.id != id }))
        return copy(placed = placed.filter { it.tile.id != id })
    }

    /** An app dropped on an app makes a folder in the target's place; dropped on a folder it joins it. */
    fun drop(movingId: Long, targetId: Long, newFolderName: String): HomeLayout? {
        val moving = find(movingId)?.tile as? AppTile ?: return null
        val target = find(targetId) ?: return null
        if (movingId == targetId) return null
        return when (val tile = target.tile) {
            is AppTile -> copy(placed = placed.mapNotNull { placed ->
                when (placed.tile.id) {
                    movingId -> null
                    targetId -> Placed(FolderTile(nextId(), newFolderName, listOf(tile, moving)), placed.spot)
                    else -> placed
                }
            })
            is FolderTile -> copy(placed = placed.filter { it.tile.id != movingId }).replace(tile.copy(apps = tile.apps + moving))
            is WidgetTile -> null
        }
    }

    fun intoNewFolder(appId: Long, name: String): HomeLayout? {
        val app = find(appId)?.tile as? AppTile ?: return null
        val folder = FolderTile(nextId(), name, listOf(app))
        return copy(placed = placed.map { if (it.tile.id == appId) Placed(folder, it.spot) else it })
    }

    fun takeOut(appId: Long): HomeLayout? {
        val folder = folderOf(appId) ?: return null
        val app = folder.apps.first { it.id == appId }
        val fromPage = (find(folder.id)?.spot?.sheet as? Sheet.Page)?.index ?: 0
        return replace(folder.copy(apps = folder.apps.filter { it.id != appId })).placeFirstFree(app, fromPage = fromPage)
    }

    fun widgetsOf(packageName: String): List<WidgetTile> =
        placed.mapNotNull { it.tile as? WidgetTile }.filter { it.provider.packageName == packageName }

    fun withoutPackage(packageName: String): HomeLayout = copy(placed = placed.mapNotNull { placed ->
        when (val tile = placed.tile) {
            is AppTile -> placed.takeIf { tile.app.packageName != packageName }
            is WidgetTile -> placed.takeIf { tile.provider.packageName != packageName }
            is FolderTile -> Placed(tile.copy(apps = tile.apps.filter { it.app.packageName != packageName }), placed.spot)
        }
    })

    fun emptyPagesBefore(page: Int): Int = (0 until page.coerceAtMost(pageCount)).count { on(Sheet.Page(it)).isEmpty() }

    /** [keep] stays even when empty: a page just added waits there for its first tile. */
    fun withoutEmptyPages(keep: Int? = null): HomeLayout {
        val used = (placed.mapNotNull { (it.spot.sheet as? Sheet.Page)?.index } + listOfNotNull(keep?.takeIf { it in 0 until pageCount })).toSortedSet()
        if (used.size == pageCount) return this
        val renumbered = used.withIndex().associate { (index, page) -> page to index }
        return copy(
            pageCount = used.size.coerceAtLeast(1),
            placed = placed.map { placed ->
                val sheet = placed.spot.sheet
                if (sheet is Sheet.Page) placed.copy(spot = placed.spot.copy(sheet = Sheet.Page(renumbered.getValue(sheet.index)))) else placed
            },
        )
    }

    /**
     * Whatever no longer fits where it was goes after the last occupied page, and a widget wider or taller than
     * the new grid is shrunk to it: a smaller grid never drops anything.
     */
    fun withSpec(newSpec: GridSpec): HomeLayout {
        if (newSpec == spec) return this
        var result = HomeLayout(newSpec, pageCount)
        val overflow = mutableListOf<Placed>()
        ordered().forEach { placed ->
            val rows = newSpec.rowsOf(placed.spot.sheet)
            val width = placed.spot.width.coerceAtMost(newSpec.columns)
            val spot = placed.spot.copy(
                column = placed.spot.column.coerceAtMost(newSpec.columns - width),
                width = width,
                height = placed.spot.height.coerceIn(1, rows.coerceAtLeast(1)),
            )
            if (rows > 0 && result.fits(spot)) result = result.place(placed.tile, spot) else overflow += placed
        }
        overflow.forEach { result = result.appendAtEnd(it.tile, it.spot.width, it.spot.height) }
        return result.withoutEmptyPages()
    }

    private fun appendAtEnd(tile: Tile, width: Int, height: Int): HomeLayout {
        val lastUsed = placed.mapNotNull { (it.spot.sheet as? Sheet.Page)?.index }.maxOrNull() ?: 0
        return placeFirstFree(tile, width, height, fromPage = lastUsed)
    }

    private fun ordered(): List<Placed> = placed.sortedWith(compareBy({ sheetOrder(it.spot.sheet) }, { it.spot.row }, { it.spot.column }))

    private fun sheetOrder(sheet: Sheet) = when (sheet) {
        Sheet.Top -> -1
        is Sheet.Page -> sheet.index
        Sheet.Bottom -> Int.MAX_VALUE
    }
}
