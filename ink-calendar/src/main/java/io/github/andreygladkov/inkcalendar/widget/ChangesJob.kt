package io.github.andreygladkov.inkcalendar.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import io.github.andreygladkov.inkcalendar.tasks.InkTasks

/**
 * A job that waits for the calendar provider, and Ink Tasks when it is read, to change, since apps no longer get the
 * calendar's broadcasts in their manifest.
 */
class ChangesJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        WidgetUpdater.drawing.execute {
            try {
                WidgetUpdater.update(context, WidgetUpdater.ids(context), force = false)
            } finally {
                schedule(context)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 1
        private const val DELAY_MS = 2_000L

        /** A content trigger fires once, so the job is scheduled again after every run. */
        fun schedule(context: Context) {
            val tasks = InkTasks(context)
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, ChangesJob::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                    .apply { if (tasks.canRead && tasks.isInstalled) addTriggerContentUri(JobInfo.TriggerContentUri(InkTasks.URI, 0)) }
                    .setTriggerContentUpdateDelay(DELAY_MS)
                    .build())
        }

        fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
