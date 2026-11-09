package io.github.andreygladkov.inkweather.forecast

import io.github.andreygladkov.inkweather.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Throws [IOException] when the service cannot be reached and [ServiceException] when it answers with an error. */
fun interface Transport {
    fun get(url: String): String
}

class ServiceException(message: String) : Exception(message)

object HttpTransport : Transport {
    private const val TIMEOUT_MS = 15_000

    /** MET Norway's terms refuse requests that do not name the app and a way to reach its author. */
    private val USER_AGENT = "InkWeather/${BuildConfig.VERSION_NAME} (+https://github.com/AndreyGladkov/ink)"

    override fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            // MET Norway answers 203 when the product version is about to be retired; the forecast in it is still good.
            if (code !in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE) throw ServiceException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
