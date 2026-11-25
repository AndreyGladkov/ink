package io.github.andreygladkov.inkweather.forecast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    /** A forecast from another source after the chosen one failed counts too, so the failing one is not waited on at every look. */
    fun isFresh(forecast: Forecast?, source: Source): Boolean =
        forecast != null && (forecast.requested == source || forecast.source == source) && forecast.age(now()) < FRESH_FOR

    fun refresh(context: Context, place: Place, source: Source): Outcome {
        val chosen = refreshFrom(context, place, source, source)
        if (chosen !is Outcome.Failed || !canFallBack(context, chosen.failure)) return chosen
        for (other in Source.entries.filter { it != source }) {
            val outcome = refreshFrom(context, place, other, source)
            if (outcome is Outcome.Updated || outcome == Outcome.Failed(Failure.STORAGE)) return outcome
        }
        return chosen
    }

    /**
     * A network error on a phone that is online means this service cannot be reached from here, so the other one may
     * be; offline or behind a captive portal the other one would only make the user wait out a second timeout.
     */
    private fun canFallBack(context: Context, failure: Failure): Boolean = when (failure) {
        Failure.SERVICE -> true
        Failure.STORAGE -> false
        Failure.NETWORK -> context.getSystemService(ConnectivityManager::class.java).let { manager ->
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
    }

    private fun refreshFrom(context: Context, place: Place, source: Source, requested: Source): Outcome {
        val fetchedAt = now()
        val body = when (val fetched = fetch(source.url(place))) {
            is Fetched.Body -> fetched.text
            is Fetched.Error -> return Outcome.Failed(fetched.failure)
        }
        val forecast = runCatching { source.forecast(body, fetchedAt, place.zone) }.getOrNull() ?: return Outcome.Failed(Failure.SERVICE)
        try {
            ForecastCache(context).save(place.id, body, fetchedAt, source, requested)
        } catch (_: IOException) {
            return Outcome.Failed(Failure.STORAGE)
        }
        return Outcome.Updated(forecast.copy(requested = requested))
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
