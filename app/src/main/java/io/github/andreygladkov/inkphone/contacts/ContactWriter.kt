package io.github.andreygladkov.inkphone.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

class ContactWriter(private val resolver: ContentResolver) {

    fun writableAccounts(): List<AccountRef> {
        val writableTypes = ContentResolver.getSyncAdapterTypes().orEmpty()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }
            .toSet()
        val accounts = LinkedHashSet<AccountRef>()
        resolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.DELETED}=0", null, null)?.use {
            while (it.moveToNext()) accounts += AccountRef(it.getString(0), it.getString(1))
        }
        return (accounts.filter { it.type == null || it.type in writableTypes } + LOCAL)
            .distinct()
            .sortedBy { if (it.type == GOOGLE) 0 else if (it.type == null) 2 else 1 }
    }

    fun load(contactId: Long, writable: List<AccountRef>): EditableContact? {
        val raws = ArrayList<Pair<Long, AccountRef>>()
        resolver.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null,
        )?.use { while (it.moveToNext()) raws += it.getLong(0) to AccountRef(it.getString(1), it.getString(2)) }
        val (rawId, account) = raws.sortedBy { (_, account) -> writable.indexOf(account).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .firstOrNull { (_, account) -> account in writable } ?: return null
        var contact = EditableContact(rawId, account)
        val projection = arrayOf(Data._ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3)
        resolver.query(Data.CONTENT_URI, projection, "${Data.RAW_CONTACT_ID}=?", arrayOf(rawId.toString()), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                contact = when (cursor.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> contact.copy(nameDataId = id, givenName = cursor.getString(3).orEmpty(), familyName = cursor.getString(4).orEmpty())
                    Phone.CONTENT_ITEM_TYPE -> contact.copy(phones = contact.phones + EditablePhone(id, cursor.getString(2).orEmpty(), cursor.getInt(3)))
                    Email.CONTENT_ITEM_TYPE -> contact.copy(emails = contact.emails + EditableEmail(id, cursor.getString(2).orEmpty()))
                    else -> contact
                }
            }
        }
        return contact
    }

    fun saveAndGetContactId(original: EditableContact?, edited: EditableContact): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = edited.rawContactId
        if (rawId == null) {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, edited.account.type)
                .withValue(RawContacts.ACCOUNT_NAME, edited.account.name)
                .build()
        }
        ContactDiff.changes(original, edited).forEach { change -> ops += operation(change, rawId) }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val savedRawId = rawId ?: results.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return null
        return resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, savedRawId), arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    fun delete(contactId: Long) {
        resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), null, null)
    }

    fun setStarred(contactId: Long, starred: Boolean) {
        val values = ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
        resolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), values, null, null)
    }

    fun setDefaultPhone(dataId: Long) {
        val values = ContentValues().apply {
            put(Data.IS_PRIMARY, 1)
            put(Data.IS_SUPER_PRIMARY, 1)
        }
        resolver.update(ContentUris.withAppendedId(Data.CONTENT_URI, dataId), values, null, null)
    }

    private fun operation(change: ContactChange, rawId: Long?): ContentProviderOperation = when (change) {
        is ContactChange.SetName -> if (change.dataId != null) {
            update(change.dataId).withValue(StructuredName.GIVEN_NAME, change.given).withValue(StructuredName.FAMILY_NAME, change.family)
                .withValue(StructuredName.DISPLAY_NAME, null).build()
        } else {
            insert(rawId, StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.GIVEN_NAME, change.given)
                .withValue(StructuredName.FAMILY_NAME, change.family).build()
        }
        is ContactChange.InsertPhone -> insert(rawId, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, change.number).withValue(Phone.TYPE, change.type).build()
        is ContactChange.UpdatePhone -> update(change.dataId).withValue(Phone.NUMBER, change.number).withValue(Phone.TYPE, change.type).build()
        is ContactChange.InsertEmail -> insert(rawId, Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, change.address).build()
        is ContactChange.UpdateEmail -> update(change.dataId).withValue(Email.ADDRESS, change.address).build()
        is ContactChange.DeleteData -> ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, change.dataId)).build()
    }

    private fun insert(rawId: Long?, mimeType: String): ContentProviderOperation.Builder {
        val builder = ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.MIMETYPE, mimeType)
        return if (rawId != null) builder.withValue(Data.RAW_CONTACT_ID, rawId) else builder.withValueBackReference(Data.RAW_CONTACT_ID, 0)
    }

    private fun update(dataId: Long) = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, dataId))

    companion object {
        const val GOOGLE = "com.google"
        const val PREF_DEFAULT_ACCOUNT = "contacts_default_account"
        val LOCAL = AccountRef(null, null)
    }
}
