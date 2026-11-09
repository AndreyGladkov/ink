package io.github.andreygladkov.inkweather.widget

import androidx.annotation.DrawableRes
import io.github.andreygladkov.inkweather.forecast.Forecast
import io.github.andreygladkov.inkweather.forecast.Place
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import java.time.Instant

/** Everything a widget shows: equal contents draw equal pixels, so an unchanged one is not sent to the panel again. */
data class WidgetContent(
    val place: String,
    val now: Slot?,
    val description: String,
    val range: String?,
    val hours: List<Slot>,
    val days: List<Slot>,
    val note: String?,
) {
    data class Slot(val label: String, @param:DrawableRes val icon: Int, val value: String)

    companion object {
        const val HOURS = 4
        const val HOUR_STEP = 3L
        const val DAYS = 4

        fun of(place: Place?, forecast: Forecast?, now: Instant, format: WeatherFormat): WidgetContent {
            val current = forecast?.current(now)
            if (place == null || forecast == null || current == null) {
                return WidgetContent(place?.name.orEmpty(), null, format.noData(), null, emptyList(), emptyList(), null)
            }
            val days = forecast.daysFrom(now, DAYS + 1)
            return WidgetContent(
                place = place.name,
                now = Slot(place.name, current.symbol.icon, format.temperature(current.temperature)),
                description = format.description(current.symbol),
                range = days.firstOrNull()?.let { format.range(it.max, it.min) },
                hours = forecast.hoursAfter(now, HOURS, HOUR_STEP).map { Slot(format.hour(it.time), it.symbol.icon, format.temperature(it.temperature)) },
                days = days.drop(1).map { Slot(format.shortDay(it.date), it.symbol.icon, format.range(it.max, it.min)) },
                note = forecast.takeIf { it.age(now) > Weather.STALE_AFTER }?.let { format.updatedShort(it.fetchedAt, now) },
            )
        }
    }
}
