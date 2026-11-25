package io.github.andreygladkov.inkweather.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.Source
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.places.PlaceStore
import java.util.concurrent.TimeUnit

class RefreshJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        Weather.fetches.execute {
            refreshWidgets(context)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 1
        private val PERIOD_MS = TimeUnit.HOURS.toMillis(1)

        /**
         * A city already fresh from the chosen source is skipped, so switching sources back and forth fetches each city
         * once. Once the chosen source has failed for one city, the rest go straight to the other source instead of each
         * waiting out the same timeout.
         */
        fun refreshWidgets(context: Context) {
            val source = Source.of(context)
            val chosenAt = Source.chosenAt(context)
            val cache = ForecastCache(context)
            var chosenAnswers = true
            PlaceStore.of(context).widgetPlaces().filterNot { Weather.isFresh(cache.load(it), source, chosenAt) }.forEach { place ->
                val outcome = Weather.refresh(context, place, source, askChosen = chosenAnswers)
                if (outcome is Weather.Outcome.Updated && outcome.forecast.source != source) chosenAnswers = false
            }
            WidgetUpdater.updateAll(context)
        }

        /**
         * The job does not wait for a network: offline it still moves widgets on to the current hour and marks how old
         * their forecast is, and the fetch just fails. Scheduling again would restart the period, so a job already
         * waiting with these settings is left alone.
         */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val pending = scheduler.getPendingJob(JOB_ID)
            if (pending != null && pending.intervalMillis == PERIOD_MS && pending.requiredNetwork == null && pending.isPersisted) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, RefreshJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build())
        }

        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
