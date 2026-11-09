package io.github.andreygladkov.inkweather.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceList
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.places.SearchActivity
import io.github.andreygladkov.inkweather.places.placeButton

class WidgetConfigActivity : Activity() {
    private val store by lazy { PlaceStore.of(this) }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, result())
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val list = PlaceList(this, getString(R.string.widget_place), ::search)
        val bound = store.widgetPlace(widgetId)?.id
        store.places.forEach { place ->
            list.rows.addView(placeButton(this, place) { choose(place) }.apply { isSelected = place.id == bound })
        }
        if (store.places.isEmpty() && savedInstanceState == null) search()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val added = SearchActivity.chosen(data)?.let { id -> store.places.firstOrNull { it.id == id } }
        when {
            added != null -> choose(added)
            store.places.isEmpty() -> finish()
        }
    }

    private fun search() {
        @Suppress("DEPRECATION")
        startActivityForResult(SearchActivity.intent(this), REQUEST_SEARCH)
    }

    private fun choose(place: Place) {
        store.bind(widgetId, place)
        RefreshJob.schedule(this)
        val context = applicationContext
        val ids = intArrayOf(widgetId)
        Weather.drawing.execute { WidgetUpdater.update(context, ids, force = true) }
        Weather.fetches.execute {
            if (Weather.isFresh(ForecastCache(context).load(place))) return@execute
            Weather.refresh(context, place)
            Weather.drawing.execute { WidgetUpdater.update(context, ids, force = false) }
        }
        setResult(RESULT_OK, result())
        finish()
    }

    private fun result() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

    private companion object {
        const val REQUEST_SEARCH = 1
    }
}
