package io.github.andreygladkov.inkclock.settings

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
import io.github.andreygladkov.inkclock.BuildConfig
import io.github.andreygladkov.inkclock.ClockPrefs
import io.github.andreygladkov.inkclock.pomodoro.PomodoroPlan
import io.github.andreygladkov.inkclock.pomodoro.PomodoroStore
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun showsEverySectionInPureBlackAndWhite() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context)).setup().get()
        val texts = texts(activity)
        listOf("Будильник", "Откладывать на", "Звонить не дольше", "Помодоро", "Длинный перерыв после", "Цель на день", "Отображение", "Вибрация", "О приложении")
            .forEach { assertTrue(it in texts, "missing $it in $texts") }
        assertPureBlackAndWhite(render(contentRoot(activity)))
    }

    @Test fun theSnoozeLengthIsRemembered() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context)).setup().get()
        contentRoot(activity).find<InkButton>().first { it.text.toString() == "5 мин" }.performClick()
        assertEquals(5, ClockPrefs.snoozeMinutes(context))
        assertEquals(10, ClockPrefs.ringMinutes(context))
    }

    @Test fun thePomodoroPlanIsRemembered() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context)).setup().get()
        contentRoot(activity).find<InkButton>().first { it.text.toString() == "50 мин" }.performClick()
        assertEquals(PomodoroPlan(workMinutes = 50), PomodoroStore.of(context).plan())
    }

    @Test fun aboutShowsTheVersionAndTheLicenses() {
        val about = Robolectric.buildActivity(AboutActivity::class.java, AboutActivity.intent(context)).setup().get()
        assertTrue(texts(about).contains("Версия ${BuildConfig.VERSION_NAME}"))
        contentRoot(about).find<InkButton>().single { it.text.toString().startsWith("Иконки Material") }.performClick()
        val license = Robolectric.buildActivity(AboutActivity::class.java, shadowOf(about).nextStartedActivity).setup().get()
        assertTrue(texts(license).any { it.contains("Apache License") && it.length > 1000 })
    }

    private fun texts(activity: Activity) = contentRoot(activity).find<InkTextView>().map { it.text.toString() }
}
