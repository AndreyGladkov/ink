package io.github.andreygladkov.inkweather.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkweather.Fixtures
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.MetNo
import io.github.andreygladkov.inkweather.forecast.WeatherFormat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class WidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val format = WeatherFormat(context, fahrenheit = false, zone = ZoneOffset.ofHours(3))
    private val forecast = MetNo.forecast(Fixtures.forecast(), Fixtures.NOW, Fixtures.MOSCOW.zone)

    @Test fun aWidgetShowsNowTodaysRangeTheNextHoursAndTheFollowingDays() {
        val content = WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW, format)
        assertEquals("Москва", content.place)
        assertEquals(WidgetContent.Slot("Москва", R.drawable.ic_sky_overcast, "−3°"), content.now)
        assertEquals("Пасмурно", content.description)
        assertEquals("18° / −4°", content.range)
        assertEquals(listOf("15" to "10°", "18" to "13°", "21" to "16°", "0" to "−5°"), content.hours.map { it.label to it.value })
        assertEquals(listOf("Вт", "Ср", "Чт", "Пт"), content.days.map { it.label })
        assertNull(content.note)
    }

    @Test fun aForecastOlderThanThreeHoursIsMarkedWithItsTime() {
        assertNull(WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW.plus(Duration.ofHours(3)), format).note)
        assertEquals("14:20", WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW.plus(Duration.ofHours(4)), format).note)
    }

    @Test fun withoutAForecastThereIsNoData() {
        val content = WidgetContent.of(Fixtures.MOSCOW, null, Fixtures.NOW, format)
        assertNull(content.now)
        assertEquals("Нет данных", content.description)
        assertEquals("", WidgetContent.of(null, forecast, Fixtures.NOW, format).place)
    }

    @Test fun equalForecastsGiveEqualContent() {
        val again = MetNo.forecast(Fixtures.forecast(), Fixtures.NOW, Fixtures.MOSCOW.zone)
        assertEquals(WidgetContent.of(Fixtures.MOSCOW, forecast, Fixtures.NOW, format), WidgetContent.of(Fixtures.MOSCOW, again, Fixtures.NOW, format))
    }
}
