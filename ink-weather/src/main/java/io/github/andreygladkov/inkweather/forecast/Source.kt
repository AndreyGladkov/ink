package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkweather.R
import java.time.Instant
import java.time.ZoneId

enum class Source(val key: String, val title: String, @param:StringRes val attribution: Int) {
    MET_NO("met_no", "MET Norway", R.string.attribution_met_no) {
        override fun url(place: Place) = MetNo.forecastUrl(place)
        override fun forecast(body: String, fetchedAt: Instant, zone: ZoneId) = MetNo.forecast(body, fetchedAt, zone, this)
    },
    OPEN_METEO("open_meteo", "Open-Meteo", R.string.attribution_open_meteo) {
        override fun url(place: Place) = OpenMeteo.forecastUrl(place)
        override fun forecast(body: String, fetchedAt: Instant, zone: ZoneId) = OpenMeteo.forecast(body, fetchedAt, zone, this)
    };

    abstract fun url(place: Place): String

    abstract fun forecast(body: String, fetchedAt: Instant, zone: ZoneId): Forecast

    companion object {
        private const val PREF = "forecast_source"

        fun byKey(key: String?): Source? = entries.firstOrNull { it.key == key }

        fun of(context: Context): Source = byKey(context.inkPrefs().getString(PREF, null)) ?: MET_NO

        fun choose(context: Context, source: Source) = context.inkPrefs().edit { putString(PREF, source.key) }
    }
}
