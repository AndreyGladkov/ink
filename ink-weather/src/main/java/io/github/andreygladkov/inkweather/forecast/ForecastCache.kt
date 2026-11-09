package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

class ForecastCache(context: Context) {
    private val dir = File(context.filesDir, "forecasts")

    fun load(place: Place): Forecast? {
        val file = file(place.id)
        val stamp = Stamp(file.path, file.lastModified(), file.length(), place.zone)
        synchronized(parsed) { parsed[place.id]?.takeIf { it.first == stamp }?.let { return it.second } }
        val forecast = runCatching {
            val saved = JSONObject(file.readText())
            MetNo.forecast(saved.getString("body"), Instant.ofEpochMilli(saved.getLong("fetchedAt")), place.zone)
        }.getOrNull() ?: return null
        synchronized(parsed) { parsed[place.id] = stamp to forecast }
        return forecast
    }

    fun save(placeId: Long, body: String, fetchedAt: Instant) {
        dir.mkdirs()
        val saved = JSONObject().put("fetchedAt", fetchedAt.toEpochMilli()).put("body", body).toString()
        val partial = File(dir, "$placeId.tmp")
        partial.writeText(saved)
        if (!partial.renameTo(file(placeId))) throw IOException("could not replace the forecast of $placeId")
    }

    fun delete(placeId: Long) {
        file(placeId).delete()
        synchronized(parsed) { parsed.remove(placeId) }
    }

    private fun file(placeId: Long) = File(dir, "$placeId.json")

    private data class Stamp(val path: String, val modified: Long, val length: Long, val zone: ZoneId)

    /** Reading a forecast parses about 90 KB of JSON; the screen and every widget read the same few, so each is parsed once per file version. */
    private companion object {
        val parsed = HashMap<Long, Pair<Stamp, Forecast>>()
    }
}
