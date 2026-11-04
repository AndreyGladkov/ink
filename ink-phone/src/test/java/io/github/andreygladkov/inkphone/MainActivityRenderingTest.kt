package io.github.andreygladkov.inkphone

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Insets
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.pixels
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkSegments
import io.github.andreygladkov.ink.ui.InkTabBar
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

    @Test fun tabBarHidesWhileKeyboardIsShown() {
        val root = contentRoot(launch())
        val bar = tabBar(root)
        root.dispatchApplyWindowInsets(keyboardInsets(visible = true))
        assertEquals(View.GONE, bar.visibility)
        root.dispatchApplyWindowInsets(keyboardInsets(visible = false))
        assertEquals(View.VISIBLE, bar.visibility)
    }

    private fun keyboardInsets(visible: Boolean) = WindowInsets.Builder()
        .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, if (visible) KEYBOARD_PX else 0))
        .setVisible(WindowInsets.Type.ime(), visible)
        .build()

    @Test fun listTabsHaveHeadersOfTheSameHeight() {
        val root = contentRoot(launch())
        val bar = tabBar(root)
        val heights = listOf(Tab.CALL_LOG, Tab.FAVORITES, Tab.CONTACTS).map { tab ->
            bar.getChildAt(tab.ordinal).performClick()
            var header: View? = null
            idleUntil {
                render(root)
                header = root.find<View>().singleOrNull { it.isShown && (it is InkSegments || it.find<InkEditText>().any { field -> field.parent === it }) }
                header != null
            }
            header!!.height
        }
        assertEquals(1, heights.distinct().size, heights.toString())
    }

    @Test fun tabBarIsCompactButKeepsTouchTargets() {
        val root = contentRoot(launch())
        render(root)
        val density = context.resources.displayMetrics.density
        val bar = tabBar(root)
        assertTrue(bar.height <= 64 * density, "tab bar ${bar.height / density}dp")
        (0 until bar.childCount).forEach { assertTrue(bar.getChildAt(it).height >= 48 * density) }
    }

    @Test fun selectedTabFillsItsCellSquare() {
        val root = contentRoot(launch())
        val bitmap = render(root)
        val bar = tabBar(root)
        val tab = (0 until bar.childCount).map(bar::getChildAt).single { it.isSelected }
        assertEquals(Color.BLACK, bitmap.getPixel(bar.left + tab.left, bar.top + tab.top))
        assertEquals(Color.BLACK, bitmap.getPixel(bar.left + tab.right - 1, bar.top + tab.bottom - 1))
    }

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

    private fun tabBar(root: ViewGroup): InkTabBar<Tab> = (0 until root.childCount).map(root::getChildAt).filterIsInstance<InkTabBar<Tab>>().single()

    private fun render(activity: Activity) = render(contentRoot(activity))

    private fun pixelsIn(bitmap: Bitmap, bar: View, tab: View): IntArray {
        val left = bar.left + tab.left
        val top = bar.top + tab.top
        return IntArray(tab.width * tab.height).also { bitmap.getPixels(it, 0, tab.width, left, top, tab.width, tab.height) }
    }

    private fun blackPixels(bitmap: Bitmap) = bitmap.pixels().count { it == Color.BLACK }

    private companion object {
        const val KEYBOARD_PX = 700
    }
}
