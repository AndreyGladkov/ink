package io.github.andreygladkov.inkcalendar

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkcalendar.ui.MiniMonthView
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class YearActivityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Before fun fixNow() = Fixtures.fixNow()

    @After fun restore() = Fixtures.restoreClock()

    @Test fun theYearShowsItsTwelveMonths() {
        val activity = open(2026)
        assertTrue(title(activity).contains("2026"))
        assertEquals((1..12).map { YearMonth.of(2026, it) }, months(activity).map { it.month })
        assertEquals("Январь 2026", months(activity).first().contentDescription)
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun arrowsTurnTheYearAndATappedMonthIsChosen() {
        val activity = open(2026)
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Следующий год" }.performClick()
        assertTrue(title(activity).contains("2027"))
        months(activity)[2].performClick()
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals(YearMonth.of(2027, 3), YearActivity.chosen(shadowOf(activity).resultIntent))
        assertTrue(activity.isFinishing)
    }

    private fun open(year: Int) = Robolectric.buildActivity(YearActivity::class.java, YearActivity.intent(application, year)).setup().get()

    private fun months(activity: Activity) = contentRoot(activity).find<MiniMonthView>()

    private fun title(activity: Activity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
