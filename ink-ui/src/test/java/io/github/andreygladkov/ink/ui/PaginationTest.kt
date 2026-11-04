package io.github.andreygladkov.ink.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PaginationTest {
    @Test fun splitsItemsCountingDividers() {
        val starts = Pagination.pageStarts(count = 7, pageHeightPx = 100, dividerPx = 2) { 32 }
        assertContentEquals(intArrayOf(0, 3, 6), starts)
    }

    @Test fun variableHeightsStartNewPageWhenNextItemDoesNotFit() {
        val heights = intArrayOf(40, 40, 30, 90, 10)
        assertContentEquals(intArrayOf(0, 2, 3), Pagination.pageStarts(heights.size, 100, 0) { heights[it] })
    }

    @Test fun itemTallerThanPageGetsItsOwnPage() {
        val heights = intArrayOf(20, 150, 20)
        assertContentEquals(intArrayOf(0, 1, 2), Pagination.pageStarts(heights.size, 100, 0) { heights[it] })
    }

    @Test fun emptyListHasOnePage() {
        assertContentEquals(intArrayOf(0), Pagination.pageStarts(0, 100, 0) { error("not measured") })
    }

    @Test fun pageOfFindsPageContainingPosition() {
        val starts = intArrayOf(0, 3, 6)
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2, 2), (0..7).map { Pagination.pageOf(starts, it) })
    }

    @Test fun breakerMeasuresOnlyWhatTheRequestedPageNeeds() {
        val measured = ArrayList<Int>()
        val breaker = PageBreaker(count = 1000, pageHeightPx = 100, dividerPx = 0) { measured += it; 30 }
        assertEquals(0, breaker.start(0))
        assertEquals(3, breaker.end(0))
        assertEquals(4, measured.size)
        assertEquals(false, breaker.isComplete)
        assertEquals(5, breaker.pageOf(16))
        assertEquals(17, measured.size)
        breaker.measure(Int.MAX_VALUE)
        assertEquals(true, breaker.isComplete)
        assertEquals(334, breaker.knownPages)
        assertEquals(1000, measured.size)
    }
}
