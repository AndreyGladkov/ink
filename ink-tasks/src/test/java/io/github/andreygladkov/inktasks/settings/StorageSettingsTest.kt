package io.github.andreygladkov.inktasks.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.findOnPages
import io.github.andreygladkov.ink.firstOnPages
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inktasks.Fixtures
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.inktasks.store.TestDocumentsProvider
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class StorageSettingsTest {
    @get:Rule val temp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun aChosenFolderTakesTheListsAndIsNamed() {
        val tree = TestDocumentsProvider.install(temp.root, "Tasks")
        Fixtures.write(context, "Дом", "- [ ] a\n")
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertTrue("Задачи хранятся только на этом телефоне." in texts(activity))
        button(activity, "Выбрать папку…").performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, asked.intent.action)
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_OK, Intent().setData(tree))
        assertEquals("- [ ] a\n", File(temp.root, "Tasks/Дом.md").readText())
        assertTrue("Папка: Tasks" in texts(activity))
        assertPureBlackAndWhite(render(contentRoot(activity)))
        button(activity, "Хранить на телефоне").performClick()
        assertNull(Storage(context).chosen)
        assertEquals("- [ ] a\n", Fixtures.read(context, "Дом"))
        assertTrue("Задачи хранятся только на этом телефоне." in texts(activity))
    }

    @Test fun aCancelledPickerChangesNothing() {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        button(activity, "Выбрать папку…").performClick()
        val asked = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(asked.intent, Activity.RESULT_CANCELED, null)
        assertNull(Storage(context).chosen)
        assertTrue("Не удалось записать в эту папку" !in texts(activity))
    }

    private fun texts(activity: SettingsActivity) = contentRoot(activity).findOnPages<InkTextView>().map { it.text.toString() }

    private fun button(activity: SettingsActivity, label: String) = contentRoot(activity).firstOnPages<InkButton> { it.text.toString() == label }
}
