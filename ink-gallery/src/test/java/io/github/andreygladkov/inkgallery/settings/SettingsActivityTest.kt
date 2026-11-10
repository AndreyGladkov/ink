package io.github.andreygladkov.inkgallery.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.pages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.inkgallery.A9
import io.github.andreygladkov.inkgallery.GalleryPrefs
import io.github.andreygladkov.inkgallery.viewer.ClearMode
import io.github.andreygladkov.inkgallery.viewer.PhotoDither
import io.github.andreygladkov.ink.ui.InkButton
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class SettingsActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun titles() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get().let { activity ->
        contentRoot(activity).findOnPages<InkChoiceRow<*>>().map { row -> row.find<InkTextView>().first().text.toString() }
    }

    private fun choose(row: String, option: String) {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        contentRoot(activity).pages().firstNotNullOf { page ->
            page.find<InkChoiceRow<*>>().firstOrNull { it.find<InkTextView>().first().text.toString() == row }
        }.find<InkButton>().single { it.text.toString() == option }.performClick()
    }

    @Test fun theClearRowIsOnlyThereWithTheA9Service() {
        assertFalse("Режим CLEAR при просмотре фото" in titles())
        A9.installService(context)
        assertTrue("Режим CLEAR при просмотре фото" in titles())
    }

    @Test fun clearForPhotosIsOnUntilTurnedOff() {
        A9.installService(context)
        assertTrue(ClearMode.isWanted(context))
        choose("Режим CLEAR при просмотре фото", "Выкл")
        assertFalse(ClearMode.isWanted(context))
    }

    @Test fun ditheringIsChosenApartFromClear() {
        assertFalse("Дизеринг фото до 16 оттенков" in titles())
        A9.installService(context)
        assertEquals(PhotoDither.AUTO, PhotoDither.of(context))
        choose("Дизеринг фото до 16 оттенков", "Выкл")
        assertEquals(PhotoDither.OFF, PhotoDither.of(context))
        assertTrue(ClearMode.isWanted(context))
    }

    @Test fun wideTurningIsOffUntilTurnedOn() {
        assertFalse(GalleryPrefs.rotatesLandscape(context))
        choose("Поворачивать горизонтальные фото на весь экран", "Вкл")
        assertTrue(GalleryPrefs.rotatesLandscape(context))
    }

    @Test fun everyPageIsBlackAndWhite() {
        A9.installService(context)
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        contentRoot(activity).pages().forEach { assertPureBlackAndWhite(render(it)) }
    }
}
