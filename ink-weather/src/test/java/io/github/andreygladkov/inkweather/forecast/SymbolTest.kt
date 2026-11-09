package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SymbolTest {
    /** Every symbol code MET Norway documents, spelling mistakes included. */
    private val documented = listOf(
        "clearsky", "fair", "partlycloudy", "cloudy", "fog",
        "lightrainshowers", "rainshowers", "heavyrainshowers", "lightrainshowersandthunder", "rainshowersandthunder", "heavyrainshowersandthunder",
        "lightsleetshowers", "sleetshowers", "heavysleetshowers", "lightssleetshowersandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder",
        "lightsnowshowers", "snowshowers", "heavysnowshowers", "lightssnowshowersandthunder", "snowshowersandthunder", "heavysnowshowersandthunder",
        "lightrain", "rain", "heavyrain", "lightrainandthunder", "rainandthunder", "heavyrainandthunder",
        "lightsleet", "sleet", "heavysleet", "lightsleetandthunder", "sleetandthunder", "heavysleetandthunder",
        "lightsnow", "snow", "heavysnow", "lightsnowandthunder", "snowandthunder", "heavysnowandthunder",
    )

    @Test fun everyDocumentedSymbolIsUnderstood() {
        documented.forEach { assertNotEquals(R.string.weather_unknown, Symbol.of(it).description, it) }
        assertEquals(R.string.weather_unknown, Symbol.of("hail").description)
    }

    @Test fun everyDocumentedWmoCodeIsUnderstood() {
        listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
            .forEach { assertNotEquals(R.string.weather_unknown, Symbol.ofWmo(it, true).description, "code $it") }
        assertEquals(R.string.weather_unknown, Symbol.ofWmo(4, true).description)
        assertEquals(Symbol(Sky.PARTLY_CLOUDY, false, R.string.weather_partly_cloudy), Symbol.ofWmo(2, false))
        assertEquals(Sky.THUNDERSTORM, Symbol.ofWmo(99, true).sky)
        assertEquals(Sky.DRIZZLE, Symbol.ofWmo(61, true).sky)
        assertEquals(R.string.freezing_drizzle, Symbol.ofWmo(56, true).description)
        assertEquals(R.string.freezing_rain, Symbol.ofWmo(67, true).description)
    }

    @Test fun theSuffixTellsDayFromNight() {
        assertTrue(Symbol.of("clearsky_day").isDay)
        assertFalse(Symbol.of("clearsky_night").isDay)
        assertFalse(Symbol.of("partlycloudy_polartwilight").isDay)
        assertTrue(Symbol.of("rain").isDay)
        assertEquals(R.drawable.ic_sky_clear_night, Symbol.of("clearsky_night").icon)
    }

    @Test fun symbolsFallIntoTheirSky() {
        assertEquals(Sky.CLEAR, Symbol.of("clearsky_day").sky)
        assertEquals(Sky.PARTLY_CLOUDY, Symbol.of("fair_night").sky)
        assertEquals(Sky.OVERCAST, Symbol.of("cloudy").sky)
        assertEquals(Sky.FOG, Symbol.of("fog").sky)
        assertEquals(Sky.DRIZZLE, Symbol.of("lightrainshowers_day").sky)
        assertEquals(Sky.RAIN, Symbol.of("heavyrain").sky)
        assertEquals(Sky.SNOW, Symbol.of("sleetshowers_night").sky)
        assertEquals(Sky.SNOW, Symbol.of("lightsnow").sky)
        assertEquals(Sky.THUNDERSTORM, Symbol.of("lightssnowshowersandthunder_day").sky)
    }

    @Test fun intensityShowersAndThunderAreRead() {
        assertEquals(Symbol(Sky.DRIZZLE, true, R.string.rain_showers_light), Symbol.of("lightrainshowers_day"))
        assertEquals(Symbol(Sky.THUNDERSTORM, false, R.string.sleet_showers_light, thunder = true), Symbol.of("lightssleetshowersandthunder_night"))
        assertEquals(Symbol(Sky.SNOW, true, R.string.snow_heavy), Symbol.of("heavysnow"))
    }
}
