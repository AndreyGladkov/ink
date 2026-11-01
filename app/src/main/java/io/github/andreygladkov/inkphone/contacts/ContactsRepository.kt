package io.github.andreygladkov.inkphone.contacts

import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.telephony.PhoneNumberUtils

class ContactsRepository(private val resolver: ContentResolver) {

    fun loadSummaries(): List<ContactSummary> {
        val numbers = HashMap<Long, MutableList<String>>()
        query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER)) { cursor ->
            val number = cursor.getString(1) ?: return@query
            numbers.getOrPut(cursor.getLong(0)) { ArrayList() }.add(number)
        }
        val accounts = loadAccounts(null)
        val contacts = ArrayList<ContactSummary>()
        val projection = arrayOf(
            Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.DISPLAY_NAME_ALTERNATIVE, Contacts.STARRED, Contacts.HAS_PHONE_NUMBER,
        )
        query(Contacts.CONTENT_URI, projection) { cursor ->
            val id = cursor.getLong(0)
            val name = cursor.getString(2).orEmpty()
            contacts += ContactSummary(
                id = id,
                lookupKey = cursor.getString(1).orEmpty(),
                name = name,
                nameAlternative = cursor.getString(3) ?: name,
                starred = cursor.getInt(4) != 0,
                hasPhone = cursor.getInt(5) != 0,
                numbers = numbers[id].orEmpty(),
                accounts = accounts[id].orEmpty().toSet(),
            )
        }
        return contacts
    }

    fun loadDetails(contactId: Long): ContactDetails? {
        var name: String? = null
        var starred = false
        query(Contacts.CONTENT_URI, arrayOf(Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED), "${Contacts._ID}=?", arrayOf(contactId.toString())) {
            name = it.getString(0).orEmpty()
            starred = it.getInt(1) != 0
        }
        val displayName = name ?: return null
        val phones = LinkedHashMap<String, PhoneEntry>()
        val emails = LinkedHashSet<String>()
        val projection = arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.IS_SUPER_PRIMARY, Data._ID)
        val selection = "${Data.CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)"
        val args = arrayOf(contactId.toString(), Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE)
        query(Data.CONTENT_URI, projection, selection, args) { cursor ->
            val value = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: return@query
            when (cursor.getString(0)) {
                Phone.CONTENT_ITEM_TYPE -> {
                    val key = PhoneNumberUtils.normalizeNumber(value).ifEmpty { value }
                    val entry = PhoneEntry(cursor.getLong(5), value, cursor.getInt(2), cursor.getString(3), cursor.getInt(4) != 0)
                    val existing = phones[key]
                    phones[key] = if (existing == null || entry.isDefault && !existing.isDefault) entry else existing
                }
                Email.CONTENT_ITEM_TYPE -> emails += value
            }
        }
        return ContactDetails(
            id = contactId,
            name = displayName,
            phones = phones.values.toList(),
            emails = emails.toList(),
            accounts = loadAccounts(contactId)[contactId].orEmpty().distinct(),
            starred = starred,
        )
    }

    private fun loadAccounts(contactId: Long?): Map<Long, List<AccountRef>> {
        val accounts = HashMap<Long, MutableList<AccountRef>>()
        val selection = buildString {
            append("${RawContacts.DELETED}=0")
            if (contactId != null) append(" AND ${RawContacts.CONTACT_ID}=?")
        }
        val args = contactId?.let { arrayOf(it.toString()) }
        query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), selection, args) { cursor ->
            accounts.getOrPut(cursor.getLong(0)) { ArrayList() }.add(AccountRef(cursor.getString(1), cursor.getString(2)))
        }
        return accounts
    }

    private fun query(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String? = null,
        args: Array<String>? = null,
        row: (Cursor) -> Unit,
    ) {
        resolver.query(uri, projection, selection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) row(cursor)
        }
    }
}
