package io.github.andreygladkov.inkweather.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
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

        fun refreshWidgets(context: Context) {
            PlaceStore.of(context).widgetPlaces().forEach { Weather.refresh(context, it) }
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
