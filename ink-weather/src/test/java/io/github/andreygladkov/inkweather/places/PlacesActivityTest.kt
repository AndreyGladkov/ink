package io.github.andreygladkov.inkweather.places

import android.app.Activity
import android.app.Application
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.findInSearch
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class PlacesActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = PlaceStore.of(context)
    private val london = Place(2643743, "Лондон", "Англия", "Великобритания", 51.50853, -0.12574)

    @After fun restore() {
        Weather.transport = HttpTransport
    }

    @Test fun citiesAreListedInOrderAndTheFirstCannotMoveUp() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        val activity = open()
        assertEquals(listOf("Москва\nРоссия", "Париж\nИль-де-Франс, Франция"), names(activity))
        val ups = buttons(activity, "Выше")
        assertEquals(listOf(View.INVISIBLE, View.VISIBLE), ups.map { it.visibility })
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun aCityMovesUp() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS, london).forEach(store::add)
        val activity = open()
        buttons(activity, "Выше")[2].performClick()
        assertEquals(listOf(Fixtures.MOSCOW, london, Fixtures.PARIS), store.places)
        assertEquals("Лондон\nАнглия, Великобритания", names(activity)[1])
    }

    @Test fun deletingAsksForASecondTap() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        val activity = open()
        val delete = buttons(activity, "Удалить")[0]
        delete.performClick()
        assertEquals(2, store.places.size)
        assertEquals("Удалить?", delete.text.toString())
        delete.performClick()
        assertEquals(listOf(Fixtures.PARIS), store.places)
        assertEquals(1, names(activity).size)
    }

    @Test fun aTapShowsTheCity() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        val activity = open()
        contentRoot(activity).find<InkButton>().single { it.text.startsWith("Париж") }.performClick()
        assertEquals(Fixtures.PARIS, store.current)
        assertTrue(activity.isFinishing)
    }

    @Test fun anAddedCityIsShown() {
        Weather.transport = Fixtures.FakeTransport { Fixtures.places(Fixtures.PARIS) }
        store.add(Fixtures.MOSCOW)
        val activity = open()
        contentRoot(activity).find<InkButton>().single { it.text.toString() == "Добавить город" }.performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, findInSearch(asked.intent, Fixtures.PARIS))
        assertEquals(listOf(Fixtures.MOSCOW, Fixtures.PARIS), store.places)
        assertEquals(Fixtures.PARIS, store.current)
        assertTrue(activity.isFinishing)
    }

    private fun open() = Robolectric.buildActivity(PlacesActivity::class.java).setup().get()

    private fun names(activity: PlacesActivity) = contentRoot(activity).find<InkButton>().map { it.text.toString() }.filter { '\n' in it }

    private fun buttons(activity: PlacesActivity, description: String) = contentRoot(activity).find<InkButton>().filter { it.contentDescription == description }
}
