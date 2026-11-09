package io.github.andreygladkov.inkweather.forecast

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** MET Norway's Locationforecast 2.0: hourly for about two and a half days, then six-hourly up to ten days, all in UTC. */
object MetNo {
    private const val FORECAST = "https://api.met.no/weatherapi/locationforecast/2.0/complete"
    private val NOON: LocalTime = LocalTime.NOON
    private const val WET_DAY_MM = 1.0
    private val SIX_HOURS: Duration = Duration.ofHours(6)

    /** MET Norway's terms ask for at most four decimals; longer coordinates are refused. */
    fun forecastUrl(place: Place) = "$FORECAST?lat=${coordinate(place.latitude)}&lon=${coordinate(place.longitude)}"

    fun forecast(body: String, fetchedAt: Instant, zone: ZoneId): Forecast {
        val series = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        val steps = (0 until series.length()).map { Step.of(series.getJSONObject(it), zone) }
        val hours = steps.mapIndexedNotNull { index, step -> step.hour(steps.getOrNull(index + 1)) }
        return Forecast(fetchedAt, zone, hours, days(steps, hours, zone))
    }

    private class Step(val instant: Instant, val time: LocalDateTime, val now: JSONObject, val next1: JSONObject?, val next6: JSONObject?, val next12: JSONObject?) {
        fun hour(next: Step?): Forecast.Hour? {
            val code = listOfNotNull(next1, next6, next12).firstNotNullOfOrNull { it.optJSONObject("summary")?.optString("symbol_code")?.takeIf(String::isNotEmpty) }
                ?: return null
            val temperature = now.number("air_temperature") ?: return null
            val hourly = next1 != null && (next == null || Duration.between(instant, next.instant) == Duration.ofHours(1))
            val fallen = (if (hourly) next1 else next6 ?: next1)?.optJSONObject("details")?.number("precipitation_amount")
            return Forecast.Hour(instant, time, temperature, now.number("apparent_air_temperature"), Symbol.of(code), fallen,
                now.number("air_pressure_at_sea_level"), now.number("wind_speed"), now.number("wind_from_direction"))
        }

        fun sixHours(): Pair<String, JSONObject>? {
            val details = next6?.optJSONObject("details") ?: return null
            val code = next6.optJSONObject("summary")?.optString("symbol_code")?.takeIf(String::isNotEmpty) ?: return null
            return code to details
        }

        companion object {
            fun of(json: JSONObject, zone: ZoneId): Step {
                val data = json.getJSONObject("data")
                val instant = Instant.parse(json.getString("time"))
                return Step(
                    instant = instant,
                    time = LocalDateTime.ofInstant(instant, zone),
                    now = data.getJSONObject("instant").getJSONObject("details"),
                    next1 = data.optJSONObject("next_1_hours"),
                    next6 = data.optJSONObject("next_6_hours"),
                    next12 = data.optJSONObject("next_12_hours"),
                )
            }
        }
    }

    /**
     * The service has no days, so each local day is put together from its hours: the extremes include every six-hour
     * span that lies wholly inside the day. The day's weather is that of its wettest six hours when at least a
     * millimetre falls, so a rainy evening is not hidden behind a dry noon; otherwise that of the six hours nearest noon.
     */
    private fun days(steps: List<Step>, hours: List<Forecast.Hour>, zone: ZoneId): List<Forecast.Day> =
        hours.groupBy { it.time.toLocalDate() }.mapNotNull { (date, dayHours) ->
            val midnight = date.plusDays(1).atStartOfDay(zone).toInstant()
            val inside = steps.filter { it.time.toLocalDate() == date && !it.instant.plus(SIX_HOURS).isAfter(midnight) }
            val spans = inside.mapNotNull { step -> step.sixHours()?.let { step to it } }
            val temperatures = dayHours.map { it.temperature } +
                spans.flatMap { (_, span) -> listOfNotNull(span.second.number("air_temperature_max"), span.second.number("air_temperature_min")) }
            val fallen = dayHours.sumOf { it.precipitationMm ?: 0.0 }
            val wettest = spans.maxByOrNull { (_, span) -> span.second.number("precipitation_amount") ?: 0.0 }?.takeIf { fallen >= WET_DAY_MM }
            val shown = wettest ?: spans.minByOrNull { (step, _) -> distanceFromNoon(date, step.time) }
            val symbol = shown?.let { (_, span) -> Symbol.of(span.first) } ?: dayHours.minBy { distanceFromNoon(date, it.time) }.symbol
            Forecast.Day(date, symbol.copy(isDay = true), temperatures.max(), temperatures.min(), fallen)
        }

    private fun distanceFromNoon(date: LocalDate, time: LocalDateTime) = abs(Duration.between(date.atTime(NOON), time).toMinutes())

    private fun coordinate(value: Double) = String.format(Locale.ROOT, "%.4f", value)

    private fun JSONObject.number(name: String): Double? = if (isNull(name)) null else optDouble(name).takeUnless { it.isNaN() }
}
