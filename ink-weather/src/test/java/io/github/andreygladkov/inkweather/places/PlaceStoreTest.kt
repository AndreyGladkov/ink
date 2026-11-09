package io.github.andreygladkov.inkweather.places

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Source
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class PlaceStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PlaceStore.of(context)
    private val cache = ForecastCache(context)
    private val london = Place(2643743, "Лондон", "Англия", "Великобритания", 51.50853, -0.12574)

    @Test fun aPlaceIsAddedOnceAndKeepsItsOrder() {
        store.add(Fixtures.MOSCOW)
        store.add(Fixtures.PARIS)
        store.add(Fixtures.MOSCOW)
        assertEquals(listOf(Fixtures.MOSCOW, Fixtures.PARIS), PlaceStore.of(context).places)
    }

    @Test fun movingUpSwapsWithThePlaceAbove() {
        listOf(Fixtures.MOSCOW, Fixtures.PARIS, london).forEach(store::add)
        store.moveUp(london.id)
        assertEquals(listOf(Fixtures.MOSCOW, london, Fixtures.PARIS), store.places)
        store.moveUp(Fixtures.MOSCOW.id)
        assertEquals(listOf(Fixtures.MOSCOW, london, Fixtures.PARIS), store.places)
    }

    @Test fun theShownPlaceFallsBackToTheFirst() {
        assertNull(store.current)
        listOf(Fixtures.MOSCOW, Fixtures.PARIS).forEach(store::add)
        assertEquals(Fixtures.MOSCOW, store.current)
        store.show(Fixtures.PARIS.id)
        assertEquals(Fixtures.PARIS, store.current)
        store.remove(Fixtures.PARIS.id)
        assertEquals(Fixtures.MOSCOW, store.current)
    }

    @Test fun aWidgetKeepsShowingAPlaceDeletedFromTheList() {
        store.add(Fixtures.MOSCOW)
        cache.save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        store.bind(7, Fixtures.MOSCOW)
        store.remove(Fixtures.MOSCOW.id)
        assertEquals(emptyList(), store.places)
        assertEquals(Fixtures.MOSCOW, store.widgetPlace(7))
        assertNotNull(cache.load(Fixtures.MOSCOW))

        store.unbind(intArrayOf(7))
        assertNull(store.widgetPlace(7))
        assertNull(cache.load(Fixtures.MOSCOW))
    }

    @Test fun aForecastStaysWhileItsPlaceIsListedOrShownByAnotherWidget() {
        store.add(Fixtures.MOSCOW)
        cache.save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        store.bind(7, Fixtures.MOSCOW)
        store.bind(8, Fixtures.MOSCOW)
        store.unbind(intArrayOf(7))
        assertNotNull(cache.load(Fixtures.MOSCOW))
        store.remove(Fixtures.MOSCOW.id)
        assertNotNull(cache.load(Fixtures.MOSCOW))
        store.unbind(intArrayOf(8))
        assertNull(cache.load(Fixtures.MOSCOW))
    }

    @Test fun aWidgetMovedToAnotherCityForgetsTheOldOnesForecast() {
        cache.save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        store.bind(7, Fixtures.MOSCOW)
        store.bind(7, Fixtures.PARIS)
        assertNull(cache.load(Fixtures.MOSCOW))
        store.add(Fixtures.PARIS)
        cache.save(Fixtures.PARIS.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        store.bind(7, Fixtures.MOSCOW)
        assertNotNull(cache.load(Fixtures.PARIS))
    }

    @Test fun widgetsAreFoundByThePlaceTheyShow() {
        store.bind(7, Fixtures.MOSCOW)
        store.bind(8, Fixtures.PARIS)
        store.bind(9, Fixtures.MOSCOW)
        store.setDrawn(9, "drawn")
        assertEquals(setOf(7, 9), store.widgetsOf(Fixtures.MOSCOW.id).toSet())
        assertEquals(setOf(Fixtures.MOSCOW, Fixtures.PARIS), store.widgetPlaces().toSet())
        store.bind(9, Fixtures.PARIS)
        assertNull(store.drawn(9))
    }
}
