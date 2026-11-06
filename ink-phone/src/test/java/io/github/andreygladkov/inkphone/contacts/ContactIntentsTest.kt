package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts
import androidx.test.core.app.ApplicationProvider
import io.github.andreygladkov.ink.A9_QUALIFIERS
import io.github.andreygladkov.ink.contentRoot
import io.github.andreygladkov.ink.find
import io.github.andreygladkov.ink.idleUntil
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkTextView
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = A9_QUALIFIERS)
class ContactIntentsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        FakeContactsProvider.reset()
        Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        FakeContactsProvider.addContact(7, "Мама", phones = listOf("+79001112233"))
    }

    @Test fun aLookupUriFromAnotherAppOpensTheCard() {
        val card = Robolectric.buildActivity(ContactCardActivity::class.java, Intent(Intent.ACTION_VIEW, Contacts.getLookupUri(7, "key7"))).setup().get()
        idleUntil { contentRoot(card).find<InkTextView>().any { it.text.toString() == "Мама" } }
    }

    @Test fun eachNumberOffersAnSmsBesideTheCall() {
        val card = Robolectric.buildActivity(ContactCardActivity::class.java, ContactCardActivity.intent(context, 7)).setup().get()
        idleUntil { contentRoot(card).find<InkButton>().any { it.contentDescription == "SMS" } }
        contentRoot(card).find<InkButton>().single { it.contentDescription == "SMS" }.performClick()
        val sms = shadowOf(card).nextStartedActivity
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data!!.scheme)
        assertEquals("+79001112233", sms.data!!.schemeSpecificPart)
    }

    @Test fun anInsertFromAnotherAppStartsANewContactWithItsNameAndNumber() {
        val insert = Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI)
            .putExtra(ContactsContract.Intents.Insert.PHONE, "+79005556677")
            .putExtra(ContactsContract.Intents.Insert.NAME, "Петя")
        val edit = Robolectric.buildActivity(ContactEditActivity::class.java, insert).setup().get()
        idleUntil { contentRoot(edit).find<InkEditText>().size >= 3 }
        val texts = contentRoot(edit).find<InkEditText>().map { it.text.toString() }
        assertEquals("Петя", texts[0])
        assertEquals("+79005556677", texts[2])
    }

    @Test fun anEditFromAnotherAppLoadsThatContact() {
        FakeContactsProvider.addContact(8, "Петя", phones = listOf("+79005556677"), accounts = listOf(ContactWriter.LOCAL))
        val edit = Robolectric.buildActivity(ContactEditActivity::class.java, Intent(Intent.ACTION_EDIT, Contacts.getLookupUri(8, "key8"))).setup().get()
        idleUntil { contentRoot(edit).find<InkEditText>().any { it.text.toString() == "+79005556677" } }
    }

    @Test fun anEditOfAContactThatIsGoneSaysSo() {
        val edit = Robolectric.buildActivity(ContactEditActivity::class.java, Intent(Intent.ACTION_EDIT, Contacts.getLookupUri(99, "key99"))).setup().get()
        idleUntil { contentRoot(edit).find<InkTextView>().any { it.isShown && it.text.toString().isNotEmpty() && it !is InkButton } }
        assertEquals(0, contentRoot(edit).find<InkEditText>().size)
    }
}
