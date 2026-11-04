package io.github.andreygladkov.inkphone.vcard

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.assertPureBlackAndWhite
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.render
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.inkphone.contacts.FakeContactsProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = A9_QUALIFIERS)
class VcardScreensTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        FakeContactsProvider.reset()
        FakeContactsProvider.addContact(1, "Мама", phones = listOf("+7 927 505-75-04"))
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }

    private fun vcfFile(text: String): Uri = Uri.fromFile(File(context.cacheDir, "test.vcf").apply { writeText(text) })

    private val twoCards = """
        BEGIN:VCARD
        VERSION:3.0
        FN:Мамочка
        TEL;TYPE=CELL:89275057504
        TEL;TYPE=HOME:+7 727 300 00 00
        END:VCARD
        BEGIN:VCARD
        VERSION:3.0
        FN:Новый Контакт
        N:Контакт;Новый;;;
        TEL;TYPE=CELL:+7 700 123 45 67
        EMAIL:new@example.com
        END:VCARD
    """.trimIndent()

    private fun openImport(): android.view.View {
        val intent = Intent(Intent.ACTION_VIEW, vcfFile(twoCards), context, ImportActivity::class.java)
        val root = contentRoot(Robolectric.buildActivity(ImportActivity::class.java, intent).setup().get())
        idleUntil { root.find<TextView>().any { it.text.toString().startsWith("В файле контактов: 2") } }
        return root
    }

    @Test fun previewShowsCountsAndDuplicatesInBlackAndWhite() {
        val root = openImport()
        val texts = root.find<TextView>().map { it.text.toString() }
        assertTrue("В файле контактов: 2, уже есть в телефоне: 1" in texts, texts.toString())
        assertTrue(texts.any { it.startsWith("Мамочка\n89275057504 · уже есть") }, texts.toString())
        assertPureBlackAndWhite(render(root))
    }

    @Test fun skippingDuplicatesInsertsOnlyNewContact() {
        val root = openImport()
        root.find<InkButton>().single { it.text.toString() == "Импортировать" }.performClick()
        idleUntil { root.find<TextView>().any { it.text.toString() == "Добавлено: 1, объединено: 0, пропущено: 1" } }
        val numbers = FakeContactsProvider.inserted.filter { it[Data.MIMETYPE] == Phone.CONTENT_ITEM_TYPE }.map { it[Phone.NUMBER] }
        assertEquals(listOf("+7 700 123 45 67"), numbers)
    }

    @Test fun failedImportIsReportedAsInterrupted() {
        FakeContactsProvider.failInserts = true
        val root = openImport()
        root.find<InkButton>().single { it.text.toString() == "Импортировать" }.performClick()
        idleUntil { root.find<TextView>().any { it.text.toString() == "Импорт прерван ошибкой. Добавлено: 0, объединено: 0" } }
    }

    @Test fun exportScreenRendersBlackAndWhite() {
        val root = contentRoot(Robolectric.buildActivity(ExportActivity::class.java).setup().get())
        idleUntil { root.find<InkButton>().any { it.text.toString() == "Выбрать контакты" } }
        assertTrue(root.find<InkButton>().any { it.text.toString() == "Сохранить в файл" })
        assertPureBlackAndWhite(render(root))
    }

    @Test fun pickerTogglesWholeRowsAndReturnsSelection() {
        val controller = Robolectric.buildActivity(ContactPickActivity::class.java, ContactPickActivity.intent(context, emptySet())).setup()
        val root = contentRoot(controller.get())
        idleUntil { root.find<InkButton>().any { it.text.toString() == "Мама" } }
        val row = root.find<InkButton>().single { it.text.toString() == "Мама" }
        row.performClick()
        assertTrue(row.isBold)
        assertTrue(root.find<InkButton>().any { it.text.toString() == "Готово (1)" })
        assertPureBlackAndWhite(render(root))
        root.find<InkButton>().single { it.text.toString() == "Готово (1)" }.performClick()
        val result = shadowOf(controller.get()).resultIntent
        assertEquals(listOf(1L), result.getLongArrayExtra(ContactPickActivity.EXTRA_SELECTED)!!.toList())
    }

    @Test fun contactCardIsReadForExport() {
        val cards = ContactCards(context.contentResolver).read(ContactCards.Scope.All)
        assertEquals(listOf("+7 927 505-75-04"), cards.single().phones.map { it.value })
    }
}
