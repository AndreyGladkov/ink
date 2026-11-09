package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object Weather {
    val FRESH_FOR: Duration = Duration.ofMinutes(30)
    val STALE_AFTER: Duration = Duration.ofHours(3)

    @VisibleForTesting
    var transport: Transport = HttpTransport

    @VisibleForTesting
    var clock: Clock = Clock.systemDefaultZone()

    /** Fetches wait up to 15 s each, so they get a thread of their own. */
    val fetches: ExecutorService = Executors.newSingleThreadExecutor()

    /** Widget drawing never waits behind a fetch: a widget broadcast is given only seconds before the system calls it hung. */
    val drawing: ExecutorService = Executors.newSingleThreadExecutor()

    enum class Failure { NETWORK, SERVICE, STORAGE }

    sealed interface Outcome {
        data class Updated(val forecast: Forecast) : Outcome
        data class Failed(val failure: Failure) : Outcome
    }

    fun now(): Instant = clock.instant()

    fun isFresh(forecast: Forecast?): Boolean = forecast != null && forecast.age(now()) < FRESH_FOR

    fun refresh(context: Context, place: Place): Outcome {
        val fetchedAt = now()
        val body = when (val fetched = fetch(MetNo.forecastUrl(place))) {
            is Fetched.Body -> fetched.text
            is Fetched.Error -> return Outcome.Failed(fetched.failure)
        }
        val forecast = runCatching { MetNo.forecast(body, fetchedAt, place.zone) }.getOrNull() ?: return Outcome.Failed(Failure.SERVICE)
        try {
            ForecastCache(context).save(place.id, body, fetchedAt)
        } catch (_: IOException) {
            return Outcome.Failed(Failure.STORAGE)
        }
        return Outcome.Updated(forecast)
    }

    sealed interface Found {
        data class Places(val places: List<Place>) : Found
        data class Failed(val failure: Failure) : Found
    }

    fun search(query: String, language: String): Found = when (val fetched = fetch(OpenMeteo.searchUrl(query, language))) {
        is Fetched.Body -> runCatching { OpenMeteo.places(fetched.text) }.getOrNull()?.let(Found::Places) ?: Found.Failed(Failure.SERVICE)
        is Fetched.Error -> Found.Failed(fetched.failure)
    }

    private sealed interface Fetched {
        data class Body(val text: String) : Fetched
        data class Error(val failure: Failure) : Fetched
    }

    private fun fetch(url: String): Fetched = try {
        Fetched.Body(transport.get(url))
    } catch (_: IOException) {
        Fetched.Error(Failure.NETWORK)
    } catch (_: ServiceException) {
        Fetched.Error(Failure.SERVICE)
    }
}
