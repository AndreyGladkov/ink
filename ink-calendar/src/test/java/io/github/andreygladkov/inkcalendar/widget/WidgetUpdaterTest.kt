package io.github.andreygladkov.inkcalendar.widget

import android.app.Application
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.inkcalendar.tasks.FakeTasksProvider
import io.github.andreygladkov.inkcalendar.tasks.InkTasks
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class WidgetUpdaterTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test fun accessToInkTasksGivenOrTakenInTheSystemSettingsIsNoticed() {
        val widgets = AppWidgetManager.getInstance(application)
        shadowOf(widgets).setAllowedToBindAppWidgets(true)
        widgets.bindAppWidgetIdIfAllowed(1, ComponentName(application, CalendarWidget::class.java))
        ChangesJob.schedule(application)
        FakeTasksProvider.install(application, granted = false)
        WidgetUpdater.noticeTasksAccess(application)
        assertFalse(InkTasks.URI in triggers())
        shadowOf(application).grantPermissions(InkTasks.PERMISSION)
        WidgetUpdater.noticeTasksAccess(application)
        assertTrue(InkTasks.URI in triggers())
        shadowOf(application).denyPermissions(InkTasks.PERMISSION)
        WidgetUpdater.noticeTasksAccess(application)
        assertFalse(InkTasks.URI in triggers())
    }

    private fun triggers(): List<Uri> =
        application.getSystemService(JobScheduler::class.java).allPendingJobs.single().triggerContentUris.orEmpty().map { it.uri }
}
