package io.github.andreygladkov.inkweather.places

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Weather
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SearchActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After fun restore() {
        Weather.transport = HttpTransport
    }

    @Test fun aSearchListsCitiesWithTheirRegionAndAddsTheChosenOne() {
        val transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS, Fixtures.MOSCOW) }
        Weather.transport = transport
        val activity = open()
        type(activity, "Пар")
        idleUntil { results(activity).size == 2 }
        assertEquals(listOf("Париж\nИль-де-Франс, Франция", "Москва\nРоссия"), results(activity).map { it.text.toString() })
        assertTrue("language=ru" in transport.asked.single())
        assertPureBlackAndWhite(render(contentRoot(activity)))

        results(activity).first().performClick()
        assertEquals(listOf(Fixtures.PARIS), PlaceStore.of(context).places)
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(Fixtures.PARIS.id, SearchActivity.chosen(shadowOf(activity).resultIntent))
    }

    @Test fun typingWaitsForAPauseBeforeAsking() {
        val transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS) }
        Weather.transport = transport
        val activity = open()
        val field = contentRoot(activity).find<InkEditText>().single()
        listOf("П", "Па", "Пар", "Пари").forEach {
            field.setText(it)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        }
        assertTrue(transport.asked.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        idleUntil { results(activity).isNotEmpty() }
        assertEquals(1, transport.asked.size)
        assertTrue("name=%D0%9F%D0%B0%D1%80%D0%B8" in transport.asked.single())
    }

    @Test fun nothingFoundAndNoConnectionAreSaid() {
        Weather.transport = Fixtures.FakeTransport { """{"generationtime_ms":0.4}""" }
        val activity = open()
        type(activity, "Ыыы")
        idleUntil { "Ничего не найдено" in status(activity) }
        Weather.transport = Fixtures.offline()
        type(activity, "Ыыыы")
        idleUntil { "Нет сети" in status(activity) }
    }

    @Test fun theSameCityCanBeSearchedAgainAfterAFailure() {
        Weather.transport = Fixtures.offline()
        val activity = open()
        type(activity, "Париж")
        idleUntil { "Нет сети" in status(activity) }
        Weather.transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS) }
        type(activity, "Париж")
        idleUntil { results(activity).isNotEmpty() }
    }

    @Test fun aSingleLetterIsNotSearched() {
        val transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS) }
        Weather.transport = transport
        val activity = open()
        type(activity, "П")
        assertTrue(transport.asked.isEmpty())
        assertTrue(listOf("Ищу…", "Ничего не найдено", "Нет сети").none { it in status(activity) })
    }

    @Test fun leavingWithoutAChoiceCancels() {
        val activity = open()
        activity.finish()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertEquals(null, SearchActivity.chosen(shadowOf(activity).resultIntent))
    }

    private fun open() = Robolectric.buildActivity(SearchActivity::class.java).setup().get()

    private fun type(activity: SearchActivity, text: String) = contentRoot(activity).find<InkEditText>().single().apply {
        setText(text)
        onEditorAction(EditorInfo.IME_ACTION_SEARCH)
    }

    private fun results(activity: SearchActivity) = contentRoot(activity).find<InkButton>().filter { it.isShown && it.text.contains('\n') }

    private fun status(activity: SearchActivity) =
        contentRoot(activity).find<InkTextView>().filter { it !is InkButton && it.visibility == View.VISIBLE && it.isShown }.map { it.text.toString() }
}
