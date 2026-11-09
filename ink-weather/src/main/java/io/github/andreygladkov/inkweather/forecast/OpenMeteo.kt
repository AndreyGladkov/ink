package io.github.andreygladkov.inkweather.forecast

import org.json.JSONObject
import java.net.URLEncoder

/** Open-Meteo's geocoding: places by name, with the time zone MET Norway's UTC forecast is shown in. */
object OpenMeteo {
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    private const val SEARCH_RESULTS = 10

    fun searchUrl(query: String, language: String) =
        "$GEOCODING?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=$SEARCH_RESULTS&language=$language&format=json"

    fun places(body: String): List<Place> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map(results::getJSONObject).map { result ->
            Place(
                id = result.getLong("id"),
                name = result.getString("name"),
                region = result.optString("admin1"),
                country = result.optString("country"),
                latitude = result.getDouble("latitude"),
                longitude = result.getDouble("longitude"),
                timezone = result.optString("timezone"),
            )
        }
    }
}
