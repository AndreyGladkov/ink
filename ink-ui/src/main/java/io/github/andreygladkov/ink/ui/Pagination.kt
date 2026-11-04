package io.github.andreygladkov.ink.ui

interface PageLayout {
    val isComplete: Boolean
    val knownPages: Int
    fun start(page: Int): Int
    fun end(page: Int): Int
    fun pageOf(position: Int): Int
    fun measure(items: Int)
}

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
) : PageLayout {
    private val starts = arrayListOf(0)
    private var next = 0
    private var used = 0

    override val isComplete: Boolean get() = next >= count

    override val knownPages: Int get() = starts.size

    override fun start(page: Int): Int = starts[page]

    override fun end(page: Int): Int {
        while (!isComplete && starts.size <= page + 1) step()
        return if (starts.size > page + 1) starts[page + 1] else count
    }

    override fun pageOf(position: Int): Int {
        while (!isComplete && next <= position) step()
        val index = starts.binarySearch(position)
        return if (index >= 0) index else (-index - 2).coerceAtLeast(0)
    }

    override fun measure(items: Int) {
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

/**
 * Fills pages from the last item backwards, so the newest page is full and only the oldest one may be short.
 * Page 0 holds the last items; higher pages go further back.
 */
class EndAnchoredPageBreaker(
    private val count: Int,
    pageHeightPx: Int,
    dividerPx: Int,
    heightOf: (Int) -> Int,
) : PageLayout {
    private val fromEnd = PageBreaker(count, pageHeightPx, dividerPx) { heightOf(count - 1 - it) }

    override val isComplete: Boolean get() = fromEnd.isComplete

    override val knownPages: Int get() = fromEnd.knownPages

    override fun start(page: Int): Int = count - fromEnd.end(page)

    override fun end(page: Int): Int {
        fromEnd.end(page)
        return count - fromEnd.start(page)
    }

    override fun pageOf(position: Int): Int = fromEnd.pageOf(count - 1 - position)

    override fun measure(items: Int) = fromEnd.measure(items)
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
