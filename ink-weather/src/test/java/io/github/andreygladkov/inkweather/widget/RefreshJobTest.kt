package io.github.andreygladkov.inkweather.widget

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.forecast.ForecastCache
import io.github.andreygladkov.inkweather.forecast.HttpTransport
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import io.github.andreygladkov.inkweather.places.PlaceStore
import io.github.andreygladkov.inkweather.forecast.Source
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefreshJobTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scheduler = context.getSystemService(JobScheduler::class.java)
    private val store = PlaceStore.of(context)

    @Before fun freezeTime() {
        Weather.clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC)
    }

    @After fun restore() {
        Weather.transport = HttpTransport
        Weather.clock = Clock.systemDefaultZone()
    }

    @Test fun theHourlyJobLivesFromTheFirstWidgetToTheLast() {
        WeatherWidget().onEnabled(context)
        val job = assertNotNull(scheduler.allPendingJobs.singleOrNull())
        assertEquals(Duration.ofHours(1).toMillis(), job.intervalMillis)
        assertTrue(job.isPersisted)
        assertNull(job.requiredNetwork)
        WeatherWidget().onDisabled(context)
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test fun schedulingAgainKeepsTheWaitingJob() {
        RefreshJob.schedule(context)
        val first = scheduler.allPendingJobs.single()
        RefreshJob.schedule(context)
        assertSame(first, scheduler.allPendingJobs.single())
    }

    @Test fun aJobLeftWaitingForANetworkIsReplaced() {
        scheduler.schedule(JobInfo.Builder(1, ComponentName(context, RefreshJob::class.java))
            .setPeriodic(Duration.ofHours(1).toMillis())
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build())
        RefreshJob.schedule(context)
        assertNull(scheduler.allPendingJobs.single().requiredNetwork)
    }

    @Test fun offlineTheWidgetStillMovesOnAndSaysHowOldItsForecastIs() {
        Weather.transport = Fixtures.offline()
        ForecastCache(context).save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(3, ComponentName(context, WeatherWidget::class.java))
        store.bind(3, Fixtures.MOSCOW)
        RefreshJob.refreshWidgets(context)
        val fresh = store.drawn(3)
        Weather.clock = Clock.fixed(Fixtures.NOW.plus(Duration.ofHours(4)), ZoneOffset.UTC)
        RefreshJob.refreshWidgets(context)
        assertNotEquals(fresh, store.drawn(3))
        assertNotNull(WidgetContent.of(Fixtures.MOSCOW, ForecastCache(context).load(Fixtures.MOSCOW), Weather.now(), WeatherFormat(context)).note)
    }

    @Test fun onlyPlacesShownByWidgetsAreFetched() {
        val transport = Fixtures.FakeTransport { Fixtures.forecast() }
        Weather.transport = transport
        store.add(Fixtures.PARIS)
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(3, ComponentName(context, WeatherWidget::class.java))
        store.bind(3, Fixtures.MOSCOW)
        RefreshJob.refreshWidgets(context)
        assertEquals(1, transport.asked.size)
        assertNotNull(ForecastCache(context).load(Fixtures.MOSCOW))
        assertNull(ForecastCache(context).load(Fixtures.PARIS))
        assertNotNull(store.drawn(3))
    }

    @Test fun aCityAlreadyFreshFromTheChosenSourceIsNotFetchedAgain() {
        val transport = Fixtures.FakeTransport { Fixtures.openMeteo() }
        Weather.transport = transport
        ForecastCache(context).save(Fixtures.MOSCOW.id, Fixtures.forecast(), Fixtures.NOW, Source.MET_NO)
        shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(3, ComponentName(context, WeatherWidget::class.java))
        store.bind(3, Fixtures.MOSCOW)
        RefreshJob.refreshWidgets(context)
        assertTrue(transport.asked.isEmpty())
        Source.choose(context, Source.OPEN_METEO)
        RefreshJob.refreshWidgets(context)
        RefreshJob.refreshWidgets(context)
        assertEquals(1, transport.asked.size)
    }

    @Test fun aRemovedWidgetForgetsItsPlace() {
        store.bind(3, Fixtures.MOSCOW)
        WeatherWidget().onDeleted(context, intArrayOf(3))
        assertNull(store.widgetPlace(3))
    }
}
