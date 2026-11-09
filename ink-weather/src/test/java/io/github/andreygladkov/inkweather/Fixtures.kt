package io.github.andreygladkov.inkweather

import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Transport
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

object Fixtures {
    val MOSCOW = Place(524901, "Москва", "Москва", "Россия", 55.75222, 37.61556, "Europe/Moscow")
    val PARIS = Place(2988507, "Париж", "Иль-де-Франс", "Франция", 48.85341, 2.3488, "Europe/Paris")
    /** 14:20 in Moscow. */
    val NOW: Instant = Instant.parse("2026-11-09T11:20:00Z")
    private val START: Instant = Instant.parse("2026-11-09T00:00:00Z")
    private const val HOURLY = 64
    private const val MOSCOW_OFFSET_HOURS = 3

    data class Span(val symbol: String, val max: Double, val min: Double, val mm: Double)

    /**
     * Hourly for 64 hours from midnight UTC, then six-hourly to ten days. An hour's temperature is −5° plus its hour in
     * Moscow; the hour of [NOW] has [current] instead. Symbols cycle through clear sky, partly cloudy, rain and light snow.
     */
    fun forecast(current: Double = -3.4, feelsLike: Double = -7.6, symbol: String = "cloudy", temperatures: Boolean = true): String {
        val hourly = (0 until HOURLY).map { i ->
            val time = START.plusSeconds(i * 3600L)
            val local = (i + MOSCOW_OFFSET_HOURS) % 24
            val isNow = time == NOW.minusSeconds(20 * 60)
            val temperature = if (isNow) current else -5.0 + local
            val code = if (isNow) symbol else cycled(i, local)
            val mm = if (isNow) 0.4 else when (i % 4) { 2 -> 0.6; 3 -> 0.2; else -> 0.0 }
            step(time, temperature.takeIf { temperatures }, code, hourMm = mm, span = Span(code, temperature + 2, temperature - 1, 1.2),
                feelsLike = if (isNow) feelsLike else temperature - 3)
        }
        val sixHourly = (0 until 30).map { j ->
            val time = START.plusSeconds((HOURLY + 2 + j * 6) * 3600L)
            step(time, j.toDouble(), "cloudy", span = Span(listOf("rain", "clearsky_day", "snow", "fog")[j % 4], j + 3.0, j - 2.0, 2.0))
        }
        return metNo(hourly + sixHourly)
    }

    /** Open-Meteo's answer for Moscow: hourly from midnight UTC for eight days, each hour's precipitation being the hour before's. */
    fun openMeteo(current: Double = -3.4): String {
        val hours = (0 until 8 * 24)
        val days = (0 until 8)
        fun local(i: Int) = (i + MOSCOW_OFFSET_HOURS) % 24
        return JSONObject()
            .put("utc_offset_seconds", MOSCOW_OFFSET_HOURS * 3600)
            .put("hourly", JSONObject()
                .put("time", JSONArray(hours.map { START.epochSecond + it * 3600L }))
                .put("temperature_2m", JSONArray(hours.map { if (it == 11) current else -5.0 + local(it) }))
                .put("apparent_temperature", JSONArray(hours.map { -9.0 + local(it) }))
                .put("weather_code", JSONArray(hours.map { if (it == 11) 3 else listOf(0, 2, 61, 71)[it % 4] }))
                .put("is_day", JSONArray(hours.map { if (local(it) in 8..16) 1 else 0 }))
                .put("precipitation", JSONArray(hours.map { it % 10 / 10.0 }))
                .put("pressure_msl", JSONArray(hours.map { 1013.2 }))
                .put("wind_speed_10m", JSONArray(hours.map { 4.4 }))
                .put("wind_direction_10m", JSONArray(hours.map { 315 })))
            .put("daily", JSONObject()
                .put("time", JSONArray(days.map { START.epochSecond - MOSCOW_OFFSET_HOURS * 3600L + it * 86400L }))
                .put("weather_code", JSONArray(days.map { listOf(3, 61, 0, 71, 95, 45, 2, 82)[it] }))
                .put("temperature_2m_max", JSONArray(days.map { 2.0 + it }))
                .put("temperature_2m_min", JSONArray(days.map { -6.0 + it }))
                .put("precipitation_sum", JSONArray(days.map { it * 1.5 })))
            .toString()
    }

    fun step(
        time: Instant,
        temperature: Double?,
        symbol: String,
        hourMm: Double? = null,
        span: Span? = null,
        feelsLike: Double? = null,
    ): JSONObject {
        val details = JSONObject()
            .put("air_pressure_at_sea_level", 1013.2)
            .put("relative_humidity", 81.7)
            .put("wind_from_direction", 315.0)
            .put("wind_speed", 4.4)
        temperature?.let { details.put("air_temperature", it) }
        feelsLike?.let { details.put("apparent_air_temperature", it) }
        val data = JSONObject().put("instant", JSONObject().put("details", details))
        if (hourMm != null) data.put("next_1_hours", summary(symbol).put("details", JSONObject().put("precipitation_amount", hourMm)))
        if (span != null) data.put("next_6_hours", summary(span.symbol).put("details", JSONObject()
            .put("air_temperature_max", span.max)
            .put("air_temperature_min", span.min)
            .put("precipitation_amount", span.mm)))
        if (hourMm == null && span == null) data.put("next_12_hours", summary(symbol).put("details", JSONObject()))
        return JSONObject().put("time", time.toString()).put("data", data)
    }

    fun metNo(steps: List<JSONObject>): String = JSONObject()
        .put("type", "Feature")
        .put("properties", JSONObject()
            .put("meta", JSONObject().put("updated_at", START.toString()))
            .put("timeseries", JSONArray(steps)))
        .toString()

    fun places(vararg places: Place): String = JSONObject().put("results", JSONArray(places.map {
        JSONObject()
            .put("id", it.id)
            .put("name", it.name)
            .put("admin1", it.region)
            .put("country", it.country)
            .put("latitude", it.latitude)
            .put("longitude", it.longitude)
            .put("timezone", it.timezone)
    })).toString()

    class FakeTransport(private val answer: (String) -> String) : Transport {
        val asked = mutableListOf<String>()

        override fun get(url: String): String {
            synchronized(asked) { asked.add(url) }
            return answer(url)
        }
    }

    fun offline() = FakeTransport { throw IOException("offline") }

    private fun cycled(index: Int, localHour: Int): String {
        val part = if (localHour in 8..16) "_day" else "_night"
        return listOf("clearsky$part", "partlycloudy$part", "rain", "lightsnow")[index % 4]
    }

    private fun summary(symbol: String) = JSONObject().put("summary", JSONObject().put("symbol_code", symbol))
}
