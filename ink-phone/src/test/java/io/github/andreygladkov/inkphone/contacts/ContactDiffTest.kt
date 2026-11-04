package io.github.andreygladkov.inkphone.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.andreygladkov.inkphone.contacts.ContactChange.DeleteData
import io.github.andreygladkov.inkphone.contacts.ContactChange.InsertEmail
import io.github.andreygladkov.inkphone.contacts.ContactChange.InsertPhone
import io.github.andreygladkov.inkphone.contacts.ContactChange.SetName
import io.github.andreygladkov.inkphone.contacts.ContactChange.UpdateEmail
import io.github.andreygladkov.inkphone.contacts.ContactChange.UpdatePhone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactDiffTest {
    private val google = AccountRef("com.google", "me@gmail.com")
    private val original = EditableContact(
        rawContactId = 7, account = google, nameDataId = 1, givenName = "Иван", familyName = "Петров",
        phones = listOf(EditablePhone(2, "+7 701 111 22 33", Phone.TYPE_MOBILE), EditablePhone(3, "+7 702 000 00 00", Phone.TYPE_WORK)),
        emails = listOf(EditableEmail(4, "ivan@example.com")),
    )

    @Test fun newContactInsertsNameAndNonBlankFields() {
        val edited = EditableContact(null, google, givenName = " Анна ", phones = listOf(EditablePhone(null, "+7 700"), EditablePhone(null, "  ")))
        assertEquals(listOf(SetName(null, "Анна", ""), InsertPhone("+7 700", Phone.TYPE_MOBILE)), ContactDiff.changes(null, edited))
    }

    @Test fun unchangedContactHasNoChanges() {
        assertEquals(emptyList(), ContactDiff.changes(original, original))
    }

    @Test fun editsUpdateDeleteAndInsertRows() {
        val edited = original.copy(
            familyName = "Петров-Водкин",
            phones = listOf(EditablePhone(2, "+7 701 111 22 33", Phone.TYPE_HOME), EditablePhone(null, "+7 777 123")),
            emails = listOf(EditableEmail(4, "ivan@new.example"), EditableEmail(null, "second@example.com")),
        )
        assertEquals(
            listOf(
                SetName(1, "Иван", "Петров-Водкин"),
                UpdatePhone(2, "+7 701 111 22 33", Phone.TYPE_HOME),
                DeleteData(3),
                InsertPhone("+7 777 123", Phone.TYPE_MOBILE),
                UpdateEmail(4, "ivan@new.example"),
                InsertEmail("second@example.com"),
            ),
            ContactDiff.changes(original, edited),
        )
    }

    @Test fun clearingAFieldDeletesIt() {
        val edited = original.copy(phones = original.phones.map { it.copy(number = " ") }, emails = emptyList())
        assertEquals(listOf(DeleteData(2), DeleteData(3), DeleteData(4)), ContactDiff.changes(original, edited))
    }

    @Test fun emptiness() {
        assertTrue(EditableContact(null, google, phones = listOf(EditablePhone(null, " "))).isEmpty)
    }

    @Test fun phoneTypesCycle() {
        assertEquals(Phone.TYPE_HOME, ContactDiff.nextPhoneType(Phone.TYPE_MOBILE))
        assertEquals(Phone.TYPE_MOBILE, ContactDiff.nextPhoneType(Phone.TYPE_OTHER))
        assertEquals(Phone.TYPE_MOBILE, ContactDiff.nextPhoneType(Phone.TYPE_FAX_WORK))
    }
}
