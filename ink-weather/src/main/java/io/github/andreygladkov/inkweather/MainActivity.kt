package io.github.andreygladkov.inkweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Source
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.places.PlacesActivity
import io.github.andreygladkov.inkweather.places.SearchActivity
import io.github.andreygladkov.inkweather.settings.SettingsActivity
import io.github.andreygladkov.inkweather.ui.ForecastView
import io.github.andreygladkov.inkweather.ui.PlaceBar
import io.github.andreygladkov.inkweather.widget.WidgetUpdater

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val store by lazy { PlaceStore.of(this) }
    private val cache by lazy { ForecastCache(this) }
    private lateinit var style: InkStyle
    private lateinit var header: LinearLayout
    private lateinit var forecast: ForecastView
    private lateinit var bar: PlaceBar
    private val loading = HashMap<Long, Source>()
    private val failures = HashMap<Long, Weather.Failure>()
    private var askingForFirstPlace = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        style = InkStyle.of(this)
        showRequested(intent)
        forecast = ForecastView(this, WeatherFormat(this)) { refresh(force = true) }
        bar = PlaceBar(this, onPrev = { step(-1) }, onNext = { step(1) })
        header = InkHeader.of(this, "", null,
            InkHeader.button(this, R.drawable.ic_list, getString(R.string.places)) { startActivity(PlacesActivity.intent(this)) },
            InkHeader.settings(this) { startActivity(SettingsActivity.intent(this)) })
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(forecast, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showRequested(intent)
    }

    override fun onResume() {
        super.onResume()
        if (InkStyle.of(this) != style) return recreate()
        if (store.places.isEmpty()) return askForFirstPlace()
        show()
        refresh(force = false)
        val context = applicationContext
        Weather.drawing.execute { WidgetUpdater.updateAll(context) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_FIRST_PLACE) return
        askingForFirstPlace = false
        SearchActivity.chosen(data)?.let(store::show)
        if (store.places.isEmpty()) finish()
    }

    private fun askForFirstPlace() {
        if (askingForFirstPlace) return
        askingForFirstPlace = true
        @Suppress("DEPRECATION")
        startActivityForResult(SearchActivity.intent(this), REQUEST_FIRST_PLACE)
    }

    /** A widget keeps showing a city deleted from the list; a tap on it puts the city back, since that is the one asked for. */
    private fun showRequested(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_PLACE, NONE)
        val place = store.places.firstOrNull { it.id == id }
            ?: store.widgetPlace(intent.getIntExtra(EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID))?.takeIf { it.id == id }?.also(store::add)
        place?.let { store.show(it.id) }
    }

    private fun show() {
        val places = store.places
        val place = store.current ?: return
        InkHeader.setTitle(header, place.name)
        forecast.show(cache.load(place), place.id in loading, failures[place.id], Source.of(this), Weather.now())
        bar.show(places.indexOfFirst { it.id == place.id }, places.size)
    }

    private fun step(by: Int) {
        val places = store.places
        val next = places.getOrNull(places.indexOfFirst { it.id == store.current?.id } + by) ?: return
        store.show(next.id)
        show()
        refresh(force = false)
    }

    /**
     * A failed refresh over a forecast already shown leaves the screen as it is instead of redrawing the same picture,
     * unless that forecast is from another source than the one chosen, which the screen then says; a forecast that
     * has run out shows nothing, so it is treated as missing. A fetch from a source left behind in the settings does
     * not stop one from the new source.
     */
    private fun refresh(force: Boolean) {
        val place = store.current ?: return
        val source = Source.of(this)
        if (loading[place.id] == source) return
        val shown = cache.load(place)?.takeIf { it.current(Weather.now()) != null }
        if (!force && Weather.isFresh(shown, source)) return
        loading[place.id] = source
        if (shown == null) {
            failures.remove(place.id)
            show()
        }
        val context = applicationContext
        Weather.fetches.execute {
            val outcome = Weather.refresh(context, place, source)
            Weather.drawing.execute { WidgetUpdater.updateFor(context, place.id) }
            main.post {
                if (loading[place.id] != source) return@post
                loading.remove(place.id)
                if (outcome is Weather.Outcome.Failed) failures[place.id] = outcome.failure else failures.remove(place.id)
                if (isDestroyed || store.current?.id != place.id) return@post
                if (outcome is Weather.Outcome.Updated || shown == null || shown.source != source) show()
            }
        }
    }

    companion object {
        private const val EXTRA_PLACE = "place"
        private const val EXTRA_WIDGET = "widget"
        private const val NONE = -1L
        private const val REQUEST_FIRST_PLACE = 1

        fun intent(context: Context, placeId: Long? = null, widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_PLACE, placeId ?: NONE).putExtra(EXTRA_WIDGET, widgetId)
    }
}
