package io.github.andreygladkov.inkweather.forecast

import org.json.JSONObject
import java.time.ZoneId

data class Place(
    val id: Long,
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "",
) {
    /** A place the geocoder gave no time zone is shown in the phone's own. */
    val zone: ZoneId get() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())

    val area: String get() = listOf(region, country).filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("region", region)
        .put("country", country)
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("timezone", timezone)

    companion object {
        fun fromJson(json: JSONObject) = Place(
            id = json.getLong("id"),
            name = json.getString("name"),
            region = json.optString("region"),
            country = json.optString("country"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            timezone = json.optString("timezone"),
        )
    }
}
