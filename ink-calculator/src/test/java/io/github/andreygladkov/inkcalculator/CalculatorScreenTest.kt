package io.github.andreygladkov.inkcalculator

import android.app.Application
import android.content.ClipboardManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkcalculator.calc.CalculatorStore
import io.github.andreygladkov.inkcalculator.calc.TapeLine
import io.github.andreygladkov.inkcalculator.ui.BottomUpColumn
import io.github.andreygladkov.inkcalculator.ui.FitTextView
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
class CalculatorScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = CalculatorStore.of(context)

    @Test fun aSumIsPreviewedThenMovesToTheTapeInBlackAndWhite() {
        val activity = open()
        assertEquals("0", expression(activity).text.toString())
        press(activity, "1", "2", "+", "3", "0", ",", "5")
        assertEquals("12 + 30,5", expression(activity).text.toString())
        assertEquals("= 42,5", preview(activity).text.toString())
        assertPureBlackAndWhite(render(contentRoot(activity)))

        press(activity, "=")
        assertEquals("42,5", expression(activity).text.toString())
        assertEquals("", preview(activity).text.toString())
        assertEquals(listOf("12 + 30,5 = 42,5"), tape(activity).map { it.text.toString() })
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theEqualsKeyIsFilled() {
        assertTrue(key(open(), "=").isSelected)
    }

    @Test fun divisionByZeroIsSaidAndKeepsTheExpression() {
        val activity = open()
        press(activity, "5", "÷", "0", "=")
        assertEquals("5 ÷ 0", expression(activity).text.toString())
        assertEquals("Делить на ноль нельзя", preview(activity).text.toString())
        press(activity, "C")
        assertEquals("0", expression(activity).text.toString())
        assertEquals("", preview(activity).text.toString())
    }

    @Test fun aTapeLineGivesItsResultToTheExpression() {
        store.remember(TapeLine("40+2", "42"))
        val activity = open()
        press(activity, "2", "×")
        tape(activity).single().performClick()
        assertEquals("2 × 42", expression(activity).text.toString())
        assertEquals("= 84", preview(activity).text.toString())
    }

    @Test fun theTapeShowsTheLatestLinesThatFitWhole() {
        (1..6).forEach { store.remember(TapeLine("$it+$it", "${it * 2}")) }
        val activity = open()
        render(contentRoot(activity))
        val column = contentRoot(activity).find<BottomUpColumn>().single()
        val shown = tape(activity).filter { it.height > 0 }
        assertTrue(shown.isNotEmpty() && shown.size < 6, "${shown.size} lines shown")
        assertEquals("6 + 6 = 12", shown.last().text.toString())
        assertTrue(shown.all { it.top >= 0 && it.bottom <= column.height })
        val hidden = (0 until column.childCount).map(column::getChildAt).filter { it.height == 0 }
        assertTrue(hidden.isNotEmpty() && hidden.all { it.visibility == View.INVISIBLE })
    }

    @Test fun theEntrySurvivesTheActivity() {
        press(open(), "7", "×", "6")
        val again = open()
        assertEquals("7 × 6", expression(again).text.toString())
        assertEquals("= 42", preview(again).text.toString())
    }

    @Test fun aLongNumberShrinksToOneLine() {
        val activity = open()
        val text = expression(activity)
        render(contentRoot(activity))
        val big = text.textScale
        press(activity, *"123456789012345".map(Char::toString).toTypedArray())
        render(contentRoot(activity))
        assertTrue(text.textScale < big, "${text.textScale}")
        assertEquals(1, text.lineCount)
    }

    @Test fun aLongResultShrinksTheOneLinePreviewAndAShortOneGrowsItBack() {
        context.inkPrefs().edit().putFloat(InkStyle.PREF_TEXT_SIZE_SP, InkStyle.TEXT_SIZES_SP.max()).commit()
        val activity = open()
        render(contentRoot(activity))
        val line = preview(activity)
        press(activity, "1", "÷", "7", "0", "0", "0", "0", "0", "0", "0")
        render(contentRoot(activity))
        assertEquals("= 1,42857142857143e−8", line.text.toString())
        assertFits(line)
        val small = line.textScale
        press(activity, "C", "2", "+", "2")
        render(contentRoot(activity))
        assertTrue(line.textScale > small, "${line.textScale} after $small")
    }

    @Test fun theExpressionGrowsBackAfterClear() {
        val activity = open()
        render(contentRoot(activity))
        val text = expression(activity)
        val big = text.textScale
        press(activity, *"123456789012345".map(Char::toString).toTypedArray())
        render(contentRoot(activity))
        press(activity, "C")
        render(contentRoot(activity))
        assertEquals(big, text.textScale)
    }

    @Test fun keysFitTheirLabelsAtTheLargestTextSize() {
        context.inkPrefs().edit().putFloat(InkStyle.PREF_TEXT_SIZE_SP, InkStyle.TEXT_SIZES_SP.max()).commit()
        val activity = open()
        render(contentRoot(activity))
        listOf("=", "+", "÷", "7").map { key(activity, it) }.forEach { key ->
            assertTrue(key.height >= key.paddingTop + key.layout.height + key.paddingBottom, "'${key.text}' is cut: ${key.height} px")
        }
    }

    @Test fun aLongPressCopiesTheValueUngroupedWithAPlainMinus() {
        val activity = open()
        press(activity, "1", "2", "3", "4", "−", "5", "0", "0", "0")
        expression(activity).performLongClick()
        assertEquals("-3766", context.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
    }

    private fun assertFits(view: InkTextView) {
        val available = view.width - view.paddingLeft - view.paddingRight
        assertTrue(view.paint.measureText(view.text.toString()) <= available, "'${view.text}' overflows $available px")
    }

    private fun open() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun press(activity: MainActivity, vararg labels: String) = labels.forEach { key(activity, it).performClick() }

    private fun key(activity: MainActivity, label: String) = contentRoot(activity).find<InkButton>()
        .first { it.text.toString() == label }

    private fun fitted(activity: MainActivity) = contentRoot(activity).find<FitTextView>()

    private fun expression(activity: MainActivity) = fitted(activity)[0]

    private fun preview(activity: MainActivity) = fitted(activity)[1]

    private fun tape(activity: MainActivity): List<InkTextView> =
        contentRoot(activity).find<BottomUpColumn>().single().let { column -> (0 until column.childCount).map { column.getChildAt(it) as InkTextView } }
            .filter { it.visibility == View.VISIBLE }
}
