package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import android.text.format.DateFormat
import androidx.core.text.util.LocalePreferences
import io.github.andreygladkov.inkweather.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class WeatherFormat(
    private val context: Context,
    private val fahrenheit: Boolean = LocalePreferences.getTemperatureUnit() == LocalePreferences.TemperatureUnit.FAHRENHEIT,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val locale = context.resources.configuration.locales[0]
    private val is24Hour = DateFormat.is24HourFormat(context)
    private val hour = DateTimeFormatter.ofPattern(if (is24Hour) "H" else "h a", locale)
    private val clock = DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val dayOfMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val decimal = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }

    val language: String get() = locale.language

    fun temperature(celsius: Double): String {
        val degrees = (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()
        return if (degrees < 0) "$MINUS${-degrees}°" else "$degrees°"
    }

    fun range(max: Double, min: Double) = "${temperature(max)} / ${temperature(min)}"

    fun noData(): String = context.getString(R.string.no_data)

    fun description(symbol: Symbol): String {
        val weather = context.getString(symbol.description)
        return if (symbol.thunder) context.getString(R.string.with_thunder, weather) else weather
    }

    fun feelsLike(celsius: Double): String = context.getString(R.string.feels_like, temperature(celsius))

    fun wind(speed: Double, degrees: Double?): String {
        val value = speed.roundToInt().toString()
        if (degrees == null) return context.getString(R.string.wind, value)
        val directions = context.resources.getStringArray(R.array.directions)
        val index = ((degrees % 360 + 360) % 360 / (360.0 / directions.size)).roundToInt() % directions.size
        return context.getString(R.string.wind_from, value, directions[index])
    }

    /** Russian forecasts give pressure in millimetres of mercury; everyone else gets hectopascals as the service sends them. */
    fun pressure(hpa: Double): String =
        if (locale.language == "ru") context.getString(R.string.pressure_mmhg, (hpa * MMHG_PER_HPA).roundToInt())
        else context.getString(R.string.pressure_hpa, hpa.roundToInt())

    fun precipitation(mm: Double): String? = mm.takeIf { it >= SHOWN_MM }?.let { context.getString(R.string.precipitation, decimal.format(it)) }

    fun amount(mm: Double?): String? = number(mm)?.let { context.getString(R.string.millimetres, it) }

    /** For a column too narrow to repeat the unit under every hour. */
    fun number(mm: Double?): String? = mm?.takeIf { it >= SHOWN_MM }?.let(decimal::format)

    fun hour(time: LocalDateTime): String = hour.format(time)

    fun day(date: LocalDate, today: LocalDate): String =
        if (date == today) context.getString(R.string.today) else "${shortDay(date)} ${date.dayOfMonth}"

    fun shortDay(date: LocalDate): String = weekday.format(date).trimEnd('.').replaceFirstChar { it.titlecase(locale) }

    fun updated(fetchedAt: Instant, now: Instant): String {
        val fetched = fetchedAt.atZone(zone)
        return if (fetched.toLocalDate() == now.atZone(zone).toLocalDate()) context.getString(R.string.updated_at, clock.format(fetched))
        else context.getString(R.string.updated_on, "${dayOfMonth.format(fetched)}, ${clock.format(fetched)}")
    }

    fun updatedShort(fetchedAt: Instant, now: Instant): String {
        val fetched = fetchedAt.atZone(zone)
        return if (fetched.toLocalDate() == now.atZone(zone).toLocalDate()) clock.format(fetched) else dayOfMonth.format(fetched)
    }

    private companion object {
        const val MINUS = '−'
        const val MMHG_PER_HPA = 0.750062
        /** Less than this rounds to "0 mm", which says nothing. */
        const val SHOWN_MM = 0.05
    }
}
