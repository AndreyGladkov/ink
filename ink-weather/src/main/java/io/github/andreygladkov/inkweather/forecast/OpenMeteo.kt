package io.github.andreygladkov.inkweather.forecast

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

object OpenMeteo {
    private const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val SEARCH_RESULTS = 10
    private const val FORECAST_DAYS = 8
    private const val HOURLY = "temperature_2m,apparent_temperature,weather_code,is_day,precipitation,pressure_msl,wind_speed_10m,wind_direction_10m"
    private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum"

    fun forecastUrl(place: Place) = "$FORECAST?latitude=${place.latitudeText}&longitude=${place.longitudeText}" +
        "&hourly=$HOURLY&daily=$DAILY&timezone=auto&timeformat=unixtime&forecast_days=$FORECAST_DAYS&wind_speed_unit=ms"

    fun forecast(body: String, fetchedAt: Instant, zone: ZoneId, source: Source): Forecast {
        val json = JSONObject(body)
        val days = days(json.getJSONObject("daily"), ZoneOffset.ofTotalSeconds(json.getInt("utc_offset_seconds")))
        return Forecast(fetchedAt, zone, hours(json.getJSONObject("hourly"), zone), days, source)
    }

    private fun hours(hourly: JSONObject, zone: ZoneId): List<Forecast.Hour> {
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val feels = hourly.optJSONArray("apparent_temperature")
        val isDay = hourly.optJSONArray("is_day")
        val fallen = hourly.optJSONArray("precipitation")
        val pressure = hourly.optJSONArray("pressure_msl")
        val wind = hourly.optJSONArray("wind_speed_10m")
        val direction = hourly.optJSONArray("wind_direction_10m")
        return (0 until times.length()).mapNotNull { i ->
            val temperature = temperatures.number(i) ?: return@mapNotNull null
            val code = codes.number(i) ?: return@mapNotNull null
            val instant = Instant.ofEpochSecond(times.getLong(i))
            // Open-Meteo gives an hour's precipitation as what fell during the hour before it, so the hour starting
            // here takes the next entry's amount, as MET Norway's next_1_hours does.
            Forecast.Hour(instant, LocalDateTime.ofInstant(instant, zone), temperature, feels?.number(i),
                Symbol.ofWmo(code.roundToInt(), isDay?.number(i) != 0.0), fallen?.number(i + 1),
                pressure?.number(i), wind?.number(i), direction?.number(i))
        }
    }

    /**
     * A day's time is its midnight under the one UTC offset Open-Meteo keeps for the whole answer, so reading it in
     * the city's zone would shift every day after a clock change by a day; hours are absolute and need no offset.
     */
    private fun days(daily: JSONObject, offset: ZoneOffset): List<Forecast.Day> {
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxima = daily.getJSONArray("temperature_2m_max")
        val minima = daily.getJSONArray("temperature_2m_min")
        val sums = daily.optJSONArray("precipitation_sum")
        return (0 until dates.length()).mapNotNull { i ->
            val code = codes.number(i) ?: return@mapNotNull null
            val max = maxima.number(i) ?: return@mapNotNull null
            val min = minima.number(i) ?: return@mapNotNull null
            val date = Instant.ofEpochSecond(dates.getLong(i)).atOffset(offset).toLocalDate()
            Forecast.Day(date, Symbol.ofWmo(code.roundToInt(), true), max, min, sums?.number(i) ?: 0.0)
        }
    }

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

    private fun JSONArray.number(index: Int): Double? = if (isNull(index)) null else optDouble(index).takeUnless { it.isNaN() }
}
