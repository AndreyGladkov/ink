package io.github.andreygladkov.inkphone.settings

import android.content.Context
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.inkphone.A9_QUALIFIERS
import io.github.andreygladkov.inkphone.assertPureBlackAndWhite
import io.github.andreygladkov.inkphone.contentRoot
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.find
import io.github.andreygladkov.inkphone.haptics.HapticChannel
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.render
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkChoiceRow
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
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun launch() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    private fun row(root: android.view.View, title: String) =
        root.find<InkChoiceRow<*>>().single { (it.getChildAt(0) as android.widget.TextView).text.toString() == title }

    private fun option(row: InkChoiceRow<*>, label: String) = row.find<InkButton>().single { it.text.toString() == label }

    @Test fun opensWithoutContactsAccess() {
        io.github.andreygladkov.inkphone.contacts.FakeContactsProvider.reset()
        Robolectric.setupContentProvider(io.github.andreygladkov.inkphone.contacts.FakeContactsProvider::class.java, android.provider.ContactsContract.AUTHORITY)
        val root = contentRoot(launch())
        assertTrue(root.find<android.widget.TextView>().none { it.text.toString() == "Аккаунты в списке" })
    }

    @Test fun rendersPureBlackAndWhiteWithRoundedButtons() {
        val root = contentRoot(launch())
        assertPureBlackAndWhite(render(root, height = 4000))
    }

    @Test fun defaultsAreSelected() {
        val root = contentRoot(launch())
        assertTrue(option(row(root, "Размер текста"), "18").isSelected)
        assertTrue(option(row(root, "Размер номера в наборе"), "24").isSelected)
        assertTrue(option(row(root, "Скругление кнопок"), "8").isSelected)
        assertTrue(option(row(root, "Клавиши набора"), "3").isSelected)
    }

    @Test fun choosingTextSizeAndCornersIsSaved() {
        val activity = launch()
        option(row(contentRoot(activity), "Размер текста"), "22").performClick()
        assertEquals(22f, InkStyle.of(context).textSizeSp)
        option(row(contentRoot(activity), "Скругление кнопок"), "0").performClick()
        assertEquals(0f, InkStyle.of(context).cornerRadiusDp)
    }

    @Test fun weightAndListModeAreSaved() {
        val activity = launch()
        option(row(contentRoot(activity), "Толщина шрифта"), "Плотный").performClick()
        assertEquals(600, InkStyle.of(context).weight)
        option(row(contentRoot(activity), "Списки"), "Прокрутка").performClick()
        assertTrue(context.inkPrefs().getBoolean(io.github.andreygladkov.inkphone.ui.InkPagedList.PREF_SCROLL, false))
    }

    @Test fun aboutShowsVersionAndLicenses() {
        val activity = Robolectric.buildActivity(AboutActivity::class.java).setup().get()
        val root = contentRoot(activity)
        val texts = root.find<android.widget.TextView>().map { it.text.toString() }
        assertTrue(texts.any { it.startsWith("Версия ") }, texts.toString())
        assertTrue("Шрифт Noto Emoji — SIL Open Font License 1.1" in texts)
        assertPureBlackAndWhite(render(root))
    }

    @Test fun licenseScreenShowsBundledText() {
        val intent = android.content.Intent(context, AboutActivity::class.java).putExtra("license", "OFL-1.1.txt")
        val root = contentRoot(Robolectric.buildActivity(AboutActivity::class.java, intent).setup().get())
        assertTrue(root.find<android.widget.TextView>().any { "SIL Open Font License" in it.text })
    }

    @Test fun choosingVibrationSavesAndPlaysIt() {
        val root = contentRoot(launch())
        option(row(root, "Клавиши набора"), "5").performClick()
        assertEquals(5, context.inkPrefs().getInt(HapticChannel.KEYS.prefKey, -1))
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        assertEquals(48L, shadowOf(vibrator).milliseconds)
    }
}
