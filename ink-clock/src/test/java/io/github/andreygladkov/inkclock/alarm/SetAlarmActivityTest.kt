package io.github.andreygladkov.inkclock.alarm

import android.app.Application
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.DayOfWeek
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class SetAlarmActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = AlarmStore.of(context)

    @Test fun anAppCanSetAnAlarmWithoutOpeningTheEditor() {
        run(Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, 6)
            .putExtra(AlarmClock.EXTRA_MINUTES, 5)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Поезд")
            .putExtra(AlarmClock.EXTRA_DAYS, arrayListOf(Calendar.SUNDAY, Calendar.MONDAY))
            .putExtra(AlarmClock.EXTRA_RINGTONE, AlarmClock.VALUE_RINGTONE_SILENT)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        assertEquals(listOf(Alarm(1, 6, 5, setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), "Поезд", ringtone = Ringtone.Silent)), store.all())
        assertEquals("Будильник заведён на 06:05", ShadowToast.getTextOfLatestToast())
    }

    @Test fun settingTheSameAlarmAgainReusesIt() {
        store.save(Alarm(4, 6, 5, isEnabled = false))
        run(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, 6).putExtra(AlarmClock.EXTRA_MINUTES, 5).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        assertEquals(listOf(Alarm(4, 6, 5)), store.all())
    }

    @Test fun withoutSkipUiTheEditorOpensPrefilled() {
        val activity = run(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, 21).putExtra(AlarmClock.EXTRA_MESSAGE, "Лекарство"))
        val editor = shadowOf(activity).nextStartedActivity
        assertEquals(AlarmEditActivity::class.java.name, editor.component?.className)
        assertEquals(21, editor.getIntExtra("hour", -1))
        assertEquals("Лекарство", editor.getStringExtra("label"))
        assertEquals(emptyList(), store.all())
    }

    private fun run(intent: Intent) = Robolectric.buildActivity(SetAlarmActivity::class.java, intent).create().get()
}
