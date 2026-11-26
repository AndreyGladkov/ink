package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class InkTimeActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun theShownTimeStartsAtNineAndIsReplacedByTypedDigits() {
        val activity = open(null)
        assertTrue(texts(activity).containsAll(listOf("Напоминание", "09:00")))
        type(activity, "1", "8", "3", "0")
        assertTrue(texts(activity).contains("18:30"))
        button(activity, "Готово").performClick()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(LocalTime.of(18, 30), InkTimeActivity.chosen(shadowOf(activity).resultIntent))
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun anImpossibleTimeIsNotTaken() {
        val activity = open(LocalTime.of(7, 45))
        assertTrue(texts(activity).contains("07:45"))
        type(activity, "9", "9", "9")
        button(activity, "Готово").performClick()
        assertFalse(activity.isFinishing)
        assertTrue(contentRoot(activity).find<InkTextView>().single { it.text.toString() == "Такого времени нет" }.isShown)
    }

    private fun open(time: LocalTime?): InkTimeActivity =
        Robolectric.buildActivity(InkTimeActivity::class.java, InkTimeActivity.intent(context, "Напоминание", time)).setup().get()

    private fun type(activity: InkTimeActivity, vararg keys: String) = keys.forEach { key -> button(activity, key).performClick() }

    private fun button(activity: InkTimeActivity, label: String) = contentRoot(activity).find<InkButton>().single { it.text.toString() == label }

    private fun texts(activity: InkTimeActivity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
