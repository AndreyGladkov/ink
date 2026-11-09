package io.github.andreygladkov.inkmessages.settings

import android.provider.Settings
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.pages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inkmessages.BuildConfig
import io.github.andreygladkov.inkmessages.TestData
import io.github.andreygladkov.inkmessages.TestData.context
import io.github.andreygladkov.inkmessages.data.FakeBlockedNumberProvider
import org.junit.Before
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
    @Before fun setUp() {
        TestData.installProviders()
    }

    @Test fun showsEverySectionInPureBlackAndWhite() {
        val activity = open()
        val texts = texts(activity)
        listOf("Открывать при запуске", "Отображение", "Вибрация", "Уведомления", "Заблокированные номера", "Приложение для SMS", "О приложении")
            .forEach { assertTrue(it in texts, "missing section $it in $texts") }
        contentRoot(activity).pages().forEach { assertPureBlackAndWhite(render(it)) }
    }

    @Test fun withoutTheSmsRoleTheBlockListIsUnavailableAndTheRoleIsOffered() {
        val activity = open()
        idleUntil { texts(activity).contains("Список доступен, когда Ink Messages выбрано для SMS.") }
        assertTrue(contentRoot(activity).findOnPages<InkButton>().any { it.text.toString() == "Сделать основным" })
    }

    @Test fun blockedNumbersCanBeUnblocked() {
        TestData.makeDefaultSmsApp()
        FakeBlockedNumberProvider.numbers += "+79001112233"
        val activity = open()
        idleUntil { contentRoot(activity).findOnPages<InkButton>().any { it.text.toString() == "Разблокировать" } }
        assertTrue(texts(activity).contains("Ink Messages выбрано для SMS."))
        contentRoot(activity).firstOnPages<InkButton> { it.text.toString() == "Разблокировать" }.performClick()
        idleUntil { texts(activity).contains("Заблокированных номеров нет.") }
        assertTrue(FakeBlockedNumberProvider.numbers.isEmpty())
    }

    @Test fun eachCategoryLinksToItsNotificationChannel() {
        val activity = open()
        contentRoot(activity).findOnPages<InkButton>().last { it.text.toString() == "Прочие" }.performClick()
        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, opened.action)
        assertEquals("messages_other", opened.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        assertEquals(context.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test fun aboutShowsTheVersionAndTheLicenses() {
        val about = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        assertTrue(texts(about).contains("Версия ${BuildConfig.VERSION_NAME}"))
        contentRoot(about).firstOnPages<InkButton> { it.text.toString().startsWith("Разбор MMS") }.performClick()
        val license = Robolectric.buildActivity(AboutActivity::class.java, shadowOf(about).nextStartedActivity).setup().get()
        assertTrue(texts(license).joinToString("\n").let { it.contains("Apache License") && it.length > 1000 })
    }

    private fun open() = Robolectric.buildActivity(SettingsActivity::class.java, SettingsActivity.intent(context)).setup().get()

    private fun texts(activity: android.app.Activity) = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }
}
