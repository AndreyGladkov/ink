package io.github.andreygladkov.inkclock.alarm

import android.app.Application
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class AlarmEditActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val store = AlarmStore.of(context)

    @Test fun aNewAlarmIsTypedOnTheKeypadAndSaved() {
        val activity = open(AlarmEditActivity.create(context))
        assertTrue("07:00" in texts(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))
        listOf("6", "4", "5").forEach { button(activity, it).performClick() }
        assertTrue("06:45" in texts(activity))
        button(activity, "Пн").performClick()
        button(activity, "Пт").performClick()
        contentRoot(activity).find<InkEditText>().single().setText("Спортзал")
        button(activity, "Сохранить").performClick()
        assertEquals(Alarm(1, 6, 45, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), "Спортзал"), store.get(1))
        assertTrue(activity.isFinishing)
    }

    @Test fun theDoubleZeroKeyTypesAWholeHour() {
        val activity = open(AlarmEditActivity.create(context))
        listOf("6", "00").forEach { button(activity, it).performClick() }
        assertTrue("06:00" in texts(activity), "${texts(activity)}")
    }

    @Test fun anImpossibleTimeIsNotSaved() {
        val activity = open(AlarmEditActivity.create(context))
        listOf("9", "7", "5").forEach { button(activity, it).performClick() }
        button(activity, "Сохранить").performClick()
        assertTrue(contentRoot(activity).find<InkTextView>().single { it.text.toString() == "Такого времени нет" }.isShown)
        assertNull(store.get(1))
    }

    @Test fun editingKeepsTheAlarmAndCanEraseDigits() {
        store.save(Alarm(5, 7, 30, label = "Подъём", isEnabled = false))
        val activity = open(AlarmEditActivity.edit(context, 5))
        assertTrue("07:30" in texts(activity))
        listOf("8", "1", "5").forEach { button(activity, it).performClick() }
        contentRoot(activity).find<InkButton>().single { it.contentDescription == "Стереть" }.performClick()
        button(activity, "0").performClick()
        button(activity, "Сохранить").performClick()
        assertEquals(Alarm(5, 8, 10, label = "Подъём"), store.get(5))
    }

    @Test fun theChosenRingtoneIsKept() {
        val activity = open(AlarmEditActivity.create(context))
        button(activity, "По умолчанию").performClick()
        val picker = shadowOf(activity).nextStartedActivityForResult
        assertEquals(RingtoneManager.ACTION_RINGTONE_PICKER, picker.intent.action)
        shadowOf(activity).receiveResult(picker.intent, android.app.Activity.RESULT_OK,
            Intent().putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, null as Uri?))
        assertTrue("Без звука" in texts(activity))
        button(activity, "Сохранить").performClick()
        assertEquals(Ringtone.Silent, store.get(1)!!.ringtone)
    }

    @Test fun deletingAsksForASecondTap() {
        store.save(Alarm(5, 7, 30))
        val activity = open(AlarmEditActivity.edit(context, 5))
        button(activity, "Удалить").performClick()
        assertEquals(Alarm(5, 7, 30), store.get(5))
        button(activity, "Точно удалить?").performClick()
        assertNull(store.get(5))
    }

    private fun open(intent: Intent) = Robolectric.buildActivity(AlarmEditActivity::class.java, intent).setup().get()

    private fun button(activity: AlarmEditActivity, label: String) = contentRoot(activity).find<InkButton>().first { it.text.toString() == label }

    private fun texts(activity: AlarmEditActivity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
