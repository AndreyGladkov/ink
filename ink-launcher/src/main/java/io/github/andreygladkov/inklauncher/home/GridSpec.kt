package io.github.andreygladkov.inklauncher.home

data class GridSpec(
    val columns: Int = 4,
    val rows: Int = 7,
    val pinnedTop: Int = 4,
    val pinnedBottom: Int = 1,
) {
    init {
        require(columns > 0 && pinnedTop >= 0 && pinnedBottom >= 0 && pagedRows >= 1) { "invalid grid $this" }
    }

    val pagedRows: Int get() = rows - pinnedTop - pinnedBottom

    fun rowsOf(sheet: Sheet): Int = when (sheet) {
        Sheet.Top -> pinnedTop
        Sheet.Bottom -> pinnedBottom
        is Sheet.Page -> pagedRows
    }

    companion object {
        val COLUMNS = 3..5
        val ROWS = 4..9
        val PINNED_BOTTOM = 0..2

        /** Pinned rows give way first, so at least one row is always left to page through. */
        fun fitted(columns: Int, rows: Int, pinnedTop: Int, pinnedBottom: Int): GridSpec {
            val bottom = pinnedBottom.coerceIn(0, rows - 1)
            return GridSpec(columns, rows, pinnedTop.coerceIn(0, rows - 1 - bottom), bottom)
        }
    }
}

sealed interface Sheet {
    data object Top : Sheet
    data object Bottom : Sheet
    data class Page(val index: Int) : Sheet

    val isPinned: Boolean get() = this !is Page
}

data class Spot(val sheet: Sheet, val column: Int, val row: Int, val width: Int = 1, val height: Int = 1) {
    fun overlaps(other: Spot) = sheet == other.sheet &&
        column < other.column + other.width && other.column < column + width &&
        row < other.row + other.height && other.row < row + height

    fun contains(column: Int, row: Int) = column in this.column until this.column + width && row in this.row until this.row + height
}

data class Placed(val tile: Tile, val spot: Spot)
