package io.github.andreygladkov.inktasks.task

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
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
class TimeActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun theShownTimeStartsAtNineAndIsReplacedByTypedDigits() {
        val activity = open(null)
        assertTrue(texts(activity).contains("09:00"))
        type(activity, "1", "8", "3", "0")
        assertTrue(texts(activity).contains("18:30"))
        button(activity, "Готово").performClick()
        assertTrue(activity.isFinishing)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(LocalTime.of(18, 30), TimeActivity.chosen(shadowOf(activity).resultIntent))
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

    private fun open(time: LocalTime?): TimeActivity =
        Robolectric.buildActivity(TimeActivity::class.java, TimeActivity.intent(context, time)).setup().get()

    private fun type(activity: TimeActivity, vararg keys: String) = keys.forEach { key -> button(activity, key).performClick() }

    private fun button(activity: TimeActivity, label: String) = contentRoot(activity).find<InkButton>().single { it.text.toString() == label }

    private fun texts(activity: TimeActivity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
