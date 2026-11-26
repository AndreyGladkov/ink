package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MonthGridTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val november = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun sixWeeksFromMondayWithTheDaysOfOtherMonthsHidden() {
        val grid = MonthGrid(context).apply { show(november, today, null) }
        assertEquals(MonthGrid.WEEKS * MonthGrid.DAYS_IN_WEEK, grid.days.size)
        assertEquals("пн", grid.find<InkTextView>().first { it !is DayCell }.text.toString().lowercase())
        assertEquals(View.INVISIBLE, grid.days.first().visibility)
        assertEquals("2026-11-01", grid.days[6].contentDescription)
    }

    @Test fun weeksStartWhereTheLanguagesLikelyCountryStartsThem() {
        assertEquals(DayOfWeek.MONDAY, MonthGrid.firstDayOfWeek(Locale.forLanguageTag("ru")))
        assertEquals(DayOfWeek.SUNDAY, MonthGrid.firstDayOfWeek(Locale.forLanguageTag("en-US")))
        assertEquals("Ноябрь 2026", MonthGrid.title(november, Locale.forLanguageTag("ru")))
    }

    @Test fun todayIsFilledTheSelectedDayRingedAndMarkedDaysDotted() {
        val grid = MonthGrid(context).apply {
            show(november, today, LocalDate.of(2026, 11, 26), setOf(LocalDate.of(2026, 11, 26)))
            setBackgroundColor(Color.WHITE)
        }
        val picture = render(grid, height = 1200)
        assertPureBlackAndWhite(picture)
        val todayCell = cell(grid, "2026-11-24")
        val selected = cell(grid, "2026-11-26")
        val plain = cell(grid, "2026-11-25")
        assertEquals(Color.BLACK, aboveTheNumber(picture, todayCell).last())
        assertEquals(Color.WHITE, aboveTheNumber(picture, selected).last())
        assertTrue(Color.BLACK in aboveTheNumber(picture, selected))
        assertTrue(Color.BLACK !in aboveTheNumber(picture, plain))
        assertEquals(1, dots(picture, selected))
        assertEquals(0, dots(picture, plain))
    }

    @Test fun aTapReportsItsDate() {
        var tapped: LocalDate? = null
        val grid = MonthGrid(context).apply {
            show(november, today, null)
            onDay = { tapped = it }
        }
        cell(grid, "2026-11-30").performClick()
        assertEquals(LocalDate.of(2026, 11, 30), tapped)
    }

    private fun cell(grid: MonthGrid, date: String) = grid.days.single { it.contentDescription == date }

    private fun origin(cell: View): IntArray {
        var x = 0
        var y = 0
        var view: View? = cell
        while (view != null && view !is MonthGrid) {
            x += view.left
            y += view.top
            view = view.parent as? View
        }
        return intArrayOf(x, y)
    }

    private fun aboveTheNumber(picture: Bitmap, cell: DayCell): List<Int> {
        val (x, y) = origin(cell)
        val numberTop = y + cell.height / 2 - (cell.textSize * 0.4f).toInt()
        return (y until numberTop).map { picture.getPixel(x + cell.width / 2, it) }
    }

    private fun dots(picture: Bitmap, cell: DayCell): Int {
        val (x, y) = origin(cell)
        val column = x + cell.width / 2
        val belowTheCircle = y + cell.height / 2 + (cell.textSize * 0.95f).toInt()
        val rows = (belowTheCircle until y + cell.height).map { picture.getPixel(column, it) == Color.BLACK }
        return rows.zipWithNext().count { (above, below) -> !above && below }
    }
}
