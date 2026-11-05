package io.github.andreygladkov.inkclock.stopwatch

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.Tab
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
class StopwatchScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = StopwatchStore.of(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Test fun startsLapsPausesAndResetsInBlackAndWhite() {
        val activity = open()
        assertTrue("00:00.0" in texts(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Старт").performClick()
        assertTrue(store.stopwatch().isRunning)
        assertTrue(titles().any { it.startsWith("Секундомер идёт с ") }, "${titles()}")
        button(activity, "Круг").performClick()
        button(activity, "Круг").performClick()
        assertEquals(2, store.stopwatch().laps.size)
        assertTrue("Круг 2" in texts(activity) && "Круг 1" in texts(activity), "${texts(activity)}")
        assertTrue(texts(activity).indexOf("Круг 2") < texts(activity).indexOf("Круг 1"))
        assertPureBlackAndWhite(render(contentRoot(activity)))

        button(activity, "Пауза").performClick()
        assertTrue(titles().any { it.startsWith("Секундомер на паузе: ") }, "${titles()}")
        button(activity, "Сброс").performClick()
        assertEquals(Stopwatch(), store.stopwatch())
        assertTrue(titles().isEmpty())
        assertTrue("00:00.0" in texts(activity))
    }

    private fun open() = Robolectric.buildActivity(MainActivity::class.java, MainActivity.intent(context, Tab.STOPWATCH)).setup().get()

    private fun button(activity: MainActivity, label: String) = contentRoot(activity).find<InkButton>().first { it.isShown && it.text.toString() == label }

    private fun texts(activity: MainActivity) = contentRoot(activity).find<InkTextView>().filter { it.isShown }.map { it.text.toString() }

    private fun titles() = notifications.activeNotifications.map { it.notification.extras.getString("android.title").orEmpty() }
}
