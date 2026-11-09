package io.github.andreygladkov.inkweather.forecast

import androidx.annotation.DrawableRes
import io.github.andreygladkov.inkweather.R

enum class Sky(@param:DrawableRes private val day: Int, @param:DrawableRes private val night: Int = day) {
    CLEAR(R.drawable.ic_sky_clear, R.drawable.ic_sky_clear_night),
    PARTLY_CLOUDY(R.drawable.ic_sky_partly, R.drawable.ic_sky_partly_night),
    OVERCAST(R.drawable.ic_sky_overcast),
    FOG(R.drawable.ic_sky_fog),
    DRIZZLE(R.drawable.ic_sky_drizzle),
    RAIN(R.drawable.ic_sky_rain),
    SNOW(R.drawable.ic_sky_snow),
    THUNDERSTORM(R.drawable.ic_sky_thunder);

    @DrawableRes
    fun icon(isDay: Boolean): Int = if (isDay) day else night
}
