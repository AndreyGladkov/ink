package io.github.andreygladkov.inkweather.forecast

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andreygladkov.inkweather.R

/** MET Norway's weather symbol, such as "lightrainshowers_day", read into what the app draws and says. */
data class Symbol(val sky: Sky, val isDay: Boolean, @param:StringRes val description: Int, val thunder: Boolean = false) {
    @get:DrawableRes
    val icon: Int get() = sky.icon(isDay)

    private enum class Intensity { LIGHT, NORMAL, HEAVY }

    private enum class Fall(val code: String, val steady: List<Int>, val showers: List<Int>) {
        RAIN("rain", listOf(R.string.rain_light, R.string.rain, R.string.rain_heavy),
            listOf(R.string.rain_showers_light, R.string.rain_showers, R.string.rain_showers_heavy)),
        SLEET("sleet", listOf(R.string.sleet_light, R.string.sleet, R.string.sleet_heavy),
            listOf(R.string.sleet_showers_light, R.string.sleet_showers, R.string.sleet_showers_heavy)),
        SNOW("snow", listOf(R.string.snow_light, R.string.snow, R.string.snow_heavy),
            listOf(R.string.snow_showers_light, R.string.snow_showers, R.string.snow_showers_heavy)),
    }

    companion object {
        private const val THUNDER = "andthunder"
        private const val SHOWERS = "showers"
        private const val LIGHT = "light"
        private const val HEAVY = "heavy"

        private val DRY = mapOf(
            "clearsky" to (Sky.CLEAR to R.string.weather_clear),
            "fair" to (Sky.PARTLY_CLOUDY to R.string.weather_fair),
            "partlycloudy" to (Sky.PARTLY_CLOUDY to R.string.weather_partly_cloudy),
            "cloudy" to (Sky.OVERCAST to R.string.weather_cloudy),
            "fog" to (Sky.FOG to R.string.weather_fog),
        )

        /** "_polartwilight" counts as night: the sun stays below the horizon. */
        fun of(code: String): Symbol {
            val name = code.substringBefore('_')
            val isDay = code.substringAfter('_', "day") == "day"
            DRY[name]?.let { (sky, description) -> return Symbol(sky, isDay, description) }
            val thunder = name.endsWith(THUNDER)
            val showers = name.removeSuffix(THUNDER).endsWith(SHOWERS)
            val body = name.removeSuffix(THUNDER).removeSuffix(SHOWERS)
            val intensity = when {
                body.startsWith(LIGHT) -> Intensity.LIGHT
                body.startsWith(HEAVY) -> Intensity.HEAVY
                else -> Intensity.NORMAL
            }
            // MET Norway spells two light symbols with a doubled "s": lightssleetshowersandthunder and lightssnowshowersandthunder.
            val fallCode = body.removePrefix(LIGHT).removePrefix(HEAVY).let { if (it == "ssleet" || it == "ssnow") it.drop(1) else it }
            val fall = Fall.entries.firstOrNull { it.code == fallCode } ?: return Symbol(Sky.OVERCAST, isDay, R.string.weather_unknown)
            val sky = when {
                thunder -> Sky.THUNDERSTORM
                fall != Fall.RAIN -> Sky.SNOW
                intensity == Intensity.LIGHT -> Sky.DRIZZLE
                else -> Sky.RAIN
            }
            return Symbol(sky, isDay, (if (showers) fall.showers else fall.steady)[intensity.ordinal], thunder)
        }
    }
}
