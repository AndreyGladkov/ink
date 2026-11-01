package io.github.andreygladkov.inkphone

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.ui.InkTabBar
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class MainActivityRenderingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun blackOnWhiteIsPureBlackAndWhite() {
        val bitmap = render(launch())
        assertPureBlackAndWhite(bitmap)
        assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
    }

    @Test fun whiteOnBlackIsPureBlackAndWhite() {
        context.inkPrefs().edit { putBoolean(InkStyle.PREF_INVERTED, true) }
        val bitmap = render(launch())
        assertPureBlackAndWhite(bitmap)
        assertEquals(Color.BLACK, bitmap.getPixel(0, 0))
    }

    @Test fun pressedButtonIsPureBlackAndWhite() {
        val activity = launch()
        val root = contentRoot(activity)
        val unpressed = render(activity)
        tabBar(root).getChildAt(0).isPressed = true
        val pressed = render(activity)
        assertPureBlackAndWhite(pressed)
        assertTrue(blackPixels(pressed) > blackPixels(unpressed))
    }

    @Test fun tabLabelsFitWithoutTruncation() {
        val root = contentRoot(launch())
        render(root)
        val bar = tabBar(root)
        (0 until bar.childCount).map { bar.getChildAt(it) as TextView }.forEach { tab ->
            val textWidth = tab.paint.measureText(tab.text.toString())
            assertTrue(textWidth <= tab.width - tab.totalPaddingLeft - tab.totalPaddingRight, "\"${tab.text}\" does not fit")
        }
    }

    @Test fun iconOnlyTabsArePureBlackAndWhite() {
        context.inkPrefs().edit { putBoolean(InkTabBar.PREF_LABELS, false) }
        val root = contentRoot(launch())
        val bitmap = render(root)
        assertPureBlackAndWhite(bitmap)
        val bar = tabBar(root)
        (0 until bar.childCount).map { bar.getChildAt(it) as TextView }.forEach { tab ->
            assertTrue(tab.text.isEmpty())
            assertTrue(!tab.contentDescription.isNullOrEmpty())
        }
    }

    @Test fun selectedTabShowsInvertedIcon() {
        listOf(true, false).forEach { labels ->
            context.inkPrefs().edit { putBoolean(InkTabBar.PREF_LABELS, labels) }
            val root = contentRoot(launch())
            val bitmap = render(root)
            val bar = tabBar(root)
            val selected = bar.getChildAt(Tab.DIALER.ordinal)
            val unselected = bar.getChildAt(Tab.FAVORITES.ordinal)
            assertTrue(pixelsIn(bitmap, bar, selected).count { it == Color.WHITE } > 0, "labels=$labels: no icon on selected tab")
            assertTrue(pixelsIn(bitmap, bar, unselected).count { it == Color.BLACK } > 0, "labels=$labels: no icon on tab")
        }
    }

    @Test fun switchingTabsReusesScreensAndShowsOnlyOne() {
        val root = contentRoot(launch())
        val bar = tabBar(root)
        val content = root.getChildAt(0) as ViewGroup
        bar.select(Tab.CONTACTS)
        val contacts = content.find<io.github.andreygladkov.inkphone.contacts.ContactsScreen>().single()
        bar.select(Tab.DIALER)
        bar.select(Tab.CONTACTS)
        assertTrue(contacts === content.find<io.github.andreygladkov.inkphone.contacts.ContactsScreen>().single())
        assertEquals(1, (0 until content.childCount).count { content.getChildAt(it).visibility == View.VISIBLE })
    }

    @Test fun tappedTabIsSelectedBeforeItsContentIsBuilt() {
        val root = contentRoot(launch())
        val bar = tabBar(root)
        val content = root.getChildAt(0) as ViewGroup
        bar.getChildAt(Tab.FAVORITES.ordinal).performClick()
        assertEquals(Tab.FAVORITES, bar.selected)
        assertTrue(content.find<io.github.andreygladkov.inkphone.favorites.FavoritesScreen>().none { it.visibility == View.VISIBLE })
        idleUntil { content.find<io.github.andreygladkov.inkphone.favorites.FavoritesScreen>().any { it.visibility == View.VISIBLE } }
    }

    @Test fun otherTabsArePreparedInBackground() {
        val root = contentRoot(launch())
        val content = root.getChildAt(0) as ViewGroup
        idleUntil { content.childCount == Tab.entries.size }
        assertEquals(1, (0 until content.childCount).count { content.getChildAt(it).visibility == View.VISIBLE })
    }

    @Test fun opensOnKeypadByDefault() {
        assertEquals(Tab.DIALER, tabBar(contentRoot(launch())).selected)
    }

    @Test fun reopensOnLastTab() {
        tabBar(contentRoot(launch())).select(Tab.CONTACTS)
        assertEquals(Tab.CONTACTS, tabBar(contentRoot(launch())).selected)
    }

    @Test fun antialiasedTextWouldBeCaughtAsGray() {
        val view = TextView(context).apply {
            text = "Контакты"
            textSize = 22f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        }
        assertTrue(render(view).pixels().any { it != Color.BLACK && it != Color.WHITE })
    }

    private fun launch(): Activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun tabBar(root: ViewGroup): InkTabBar = (0 until root.childCount).map(root::getChildAt).filterIsInstance<InkTabBar>().single()

    private fun render(activity: Activity) = render(contentRoot(activity))

    private fun pixelsIn(bitmap: Bitmap, bar: View, tab: View): IntArray {
        val left = bar.left + tab.left
        val top = bar.top + tab.top
        return IntArray(tab.width * tab.height).also { bitmap.getPixels(it, 0, tab.width, left, top, tab.width, tab.height) }
    }

    private fun blackPixels(bitmap: Bitmap) = bitmap.pixels().count { it == Color.BLACK }
}
