package io.github.andreygladkov.inkweather.places

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Place
import org.json.JSONArray
import org.json.JSONObject

/** A widget keeps its own copy of its place, so deleting the city from the list leaves the widget showing it. */
class PlaceStore private constructor(private val prefs: SharedPreferences, private val cache: ForecastCache) {
    val places: List<Place>
        get() = prefs.getString(PLACES, null)?.let { saved ->
            runCatching { JSONArray(saved).let { array -> (0 until array.length()).map { Place.fromJson(array.getJSONObject(it)) } } }.getOrNull()
        }.orEmpty()

    val current: Place?
        get() {
            val all = places
            val id = prefs.getLong(CURRENT, NONE)
            return all.firstOrNull { it.id == id } ?: all.firstOrNull()
        }

    fun show(placeId: Long) = prefs.edit { putLong(CURRENT, placeId) }

    fun add(place: Place) {
        val all = places
        if (all.none { it.id == place.id }) save(all + place)
    }

    fun remove(placeId: Long) {
        save(places.filterNot { it.id == placeId })
        forgetIfUnused(placeId)
    }

    fun moveUp(placeId: Long) {
        val all = places.toMutableList()
        val index = all.indexOfFirst { it.id == placeId }
        if (index <= 0) return
        all.add(index - 1, all.removeAt(index))
        save(all)
    }

    fun bind(widgetId: Int, place: Place) {
        val previous = widgetPlace(widgetId)?.id
        prefs.edit {
            putString(widgetKey(widgetId), place.toJson().toString())
            remove(drawnKey(widgetId))
        }
        if (previous != null && previous != place.id) forgetIfUnused(previous)
    }

    fun widgetPlace(widgetId: Int): Place? =
        prefs.getString(widgetKey(widgetId), null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() }

    fun unbind(ids: IntArray) {
        val unbound = ids.toList().mapNotNull { widgetPlace(it)?.id }.distinct()
        prefs.edit {
            ids.forEach {
                remove(widgetKey(it))
                remove(drawnKey(it))
            }
        }
        unbound.forEach(::forgetIfUnused)
    }

    fun widgetIds(): List<Int> = prefs.all.keys.filter { it.startsWith(WIDGET) }.mapNotNull { it.removePrefix(WIDGET).toIntOrNull() }

    fun widgetsOf(placeId: Long): List<Int> = widgetIds().filter { widgetPlace(it)?.id == placeId }

    fun widgetPlaces(): List<Place> = widgetIds().mapNotNull(::widgetPlace).distinctBy { it.id }

    fun drawn(widgetId: Int): String? = prefs.getString(drawnKey(widgetId), null)

    fun setDrawn(widgetId: Int, key: String) = prefs.edit { putString(drawnKey(widgetId), key) }

    private fun forgetIfUnused(placeId: Long) {
        if (places.none { it.id == placeId } && widgetsOf(placeId).isEmpty()) cache.delete(placeId)
    }

    private fun save(places: List<Place>) = prefs.edit { putString(PLACES, JSONArray(places.map { it.toJson() }).toString()) }

    private fun widgetKey(widgetId: Int) = "$WIDGET$widgetId"

    private fun drawnKey(widgetId: Int) = "$DRAWN$widgetId"

    companion object {
        private const val FILE = "places"
        private const val PLACES = "places"
        private const val CURRENT = "current"
        private const val WIDGET = "widget_"
        private const val DRAWN = "drawn_"
        private const val NONE = -1L

        fun of(context: Context) = PlaceStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), ForecastCache(context))
    }
}
