package io.github.andreygladkov.ink.ui

/**
 * Splits items into pages lazily, measuring only as far as the requested page needs.
 * [forcedStart] always begins a new page, so a jump lands with that item on top instead of mid-page.
 */
class PageBreaker(
    private val count: Int,
    private val pageHeightPx: Int,
    private val dividerPx: Int,
    private val forcedStart: Int? = null,
    private val heightOf: (Int) -> Int,
) {
    private val starts = arrayListOf(0)
    private var next = 0
    private var used = 0

    val isComplete: Boolean get() = next >= count

    val knownPages: Int get() = starts.size

    fun start(page: Int): Int = starts[page]

    fun end(page: Int): Int {
        while (!isComplete && starts.size <= page + 1) step()
        return if (starts.size > page + 1) starts[page + 1] else count
    }

    fun pageOf(position: Int): Int {
        while (!isComplete && next <= position) step()
        val index = starts.binarySearch(position)
        return if (index >= 0) index else (-index - 2).coerceAtLeast(0)
    }

    fun measure(items: Int) {
        repeat(items) { if (!isComplete) step() }
    }

    private fun step() {
        val height = heightOf(next)
        if (used > 0 && (next == forcedStart || used + dividerPx + height > pageHeightPx)) {
            starts += next
            used = height
        } else {
            used += if (used > 0) dividerPx + height else height
        }
        next++
    }
}

object Pagination {
    fun pageStarts(count: Int, pageHeightPx: Int, dividerPx: Int, heightOf: (Int) -> Int): IntArray {
        val breaker = PageBreaker(count, pageHeightPx, dividerPx, heightOf = heightOf)
        breaker.measure(count)
        return IntArray(breaker.knownPages, breaker::start)
    }

    fun pageOf(starts: IntArray, position: Int): Int {
        val index = starts.binarySearch(position)
        return if (index >= 0) index else (-index - 2).coerceAtLeast(0)
    }
}
