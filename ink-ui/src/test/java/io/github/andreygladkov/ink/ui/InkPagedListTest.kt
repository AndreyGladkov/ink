package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.A9_HEIGHT
import io.github.andreygladkov.ink.A9_WIDTH
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.inkPrefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class InkPagedListTest {
    @Test fun alikeItemsArePagedAtOnceWithoutBuildingThemAll() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val list = InkPagedList(activity, dividers = false)
        activity.setContentView(FrameLayout(activity).apply { addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1200)) })
        val built = mutableListOf<Int>()
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = 10_000
            override fun itemId(position: Int) = position.toLong()
            override fun uniformHeight(widthPx: Int) = 100
            override fun createView(position: Int): View = TextView(activity).apply {
                built += position
                text = "item $position"
                height = 100
            }
        })
        shadowOf(Looper.getMainLooper()).idle()
        val perPage = list.positionsOf(0)!!.count()
        assertTrue(perPage in 9..11, "a page holds $perPage")
        assertEquals((10_000 + perPage - 1) / perPage, list.pageCount)
        assertEquals(perPage until 2 * perPage, list.positionsOf(1))
        assertEquals(null, list.positionsOf(list.pageCount))
        assertTrue(built.distinct().size <= perPage, "built ${built.distinct().size} items")
    }

    @Test fun aChatAsksPastItsOldestPageGetsNothing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val list = InkPagedList(activity, anchorEnd = true, dividers = false)
        activity.setContentView(FrameLayout(activity).apply { addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1200)) })
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = 40
            override fun itemId(position: Int) = position.toLong()
            override fun createView(position: Int): View = TextView(activity).apply { height = 100 }
        }, position = 39)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(null, list.positionsOf(list.pageCount))
        list.showPage(list.pageCount + 1)
        assertEquals(list.pageCount - 1, list.page)
    }

    @Test fun chatStaysOnTheNewestItemsWhenItGetsShorter() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val list = InkPagedList(activity, anchorEnd = true, dividers = false)
        activity.setContentView(FrameLayout(activity).apply { addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1200)) })
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = 40
            override fun itemId(position: Int) = position.toLong()
            override fun createView(position: Int): View = TextView(activity).apply {
                text = "item $position"
                height = 100
            }
        }, position = 39)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("item 39", shown(list).last())
        list.layoutParams = list.layoutParams.apply { height = 700 }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("item 39", shown(list).last())
        assertEquals(0, list.page)
    }

    @Test fun aFooterViewSitsBesideThePageButtonsOrAloneWhenScrolling() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val paged = InkPagedList(activity)
        val home = InkButton(activity).apply { icon = R.drawable.ic_page_down }
        paged.addFooterView(home)
        val row = home.parent as LinearLayout
        assertEquals(home, row.getChildAt(row.childCount - 1))
        assertTrue(row.getChildAt(row.childCount - 2).background is VerticalLineDrawable)
        val next = row.find<InkButton>().single { it.contentDescription == activity.getString(R.string.page_next) }
        render(paged, A9_WIDTH, A9_HEIGHT)
        assertEquals(next.minHeight, row.height)
        assertEquals(row.height, home.height)

        activity.inkPrefs().edit().putBoolean(InkPagedList.PREF_SCROLL, true).commit()
        val scrolling = InkPagedList(activity)
        val back = InkButton(activity).apply { icon = R.drawable.ic_page_down }
        scrolling.addFooterView(back, atStart = true)
        val own = back.parent as LinearLayout
        assertEquals(back, own.getChildAt(0))
        assertEquals(2, own.childCount)
        render(scrolling, A9_WIDTH, A9_HEIGHT)
        assertEquals(back.minHeight, own.height)
    }

    @Test fun aScrollingListKeepsItsOffsetWhenItsRowsAreReplaced() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.inkPrefs().edit().putBoolean(InkPagedList.PREF_SCROLL, true).commit()
        val list = InkPagedList(activity, dividers = false)
        activity.setContentView(FrameLayout(activity).apply { addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 1200)) })
        list.setAdapter(rows(activity))
        shadowOf(Looper.getMainLooper()).idle()
        val scrolling = list.find<android.widget.ListView>().single()
        scrolling.setSelectionFromTop(5, -40)
        shadowOf(Looper.getMainLooper()).idle()
        list.replaceAdapter(rows(activity), list.firstPosition)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(5, scrolling.firstVisiblePosition)
        assertEquals(-40, scrolling.getChildAt(0).top)
    }

    private fun rows(activity: Activity) = object : InkPagedList.Adapter {
        override val count = 40
        override fun itemId(position: Int) = position.toLong()
        override fun createView(position: Int): View = TextView(activity).apply {
            text = "item $position"
            height = 100
        }
    }

    private fun shown(list: InkPagedList) = list.find<TextView>().filter { it.text.startsWith("item") }.map { it.text.toString() }
}
