package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.find
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class InkPagedListTest {
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

    private fun shown(list: InkPagedList) = list.find<TextView>().filter { it.text.startsWith("item") }.map { it.text.toString() }
}
