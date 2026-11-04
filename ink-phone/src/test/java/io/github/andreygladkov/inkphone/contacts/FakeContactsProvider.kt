package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

class FakeContactsProvider : ContentProvider() {
    override fun onCreate() = true

    private fun require(permission: String) {
        if (context!!.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Permission Denial: requires $permission")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
        require(Manifest.permission.READ_CONTACTS)
        if (uri.path!!.startsWith("/phone_lookup/")) return phoneLookup(uri.lastPathSegment!!, projection!!)
        val rows = when (uri.path) {
            "/contacts" -> contacts.filter { args == null || it[Contacts._ID].toString() == args[0] }
            "/data/phones" -> data.filter { it[Data.MIMETYPE] == Phone.CONTENT_ITEM_TYPE }
            "/raw_contacts" -> rawContacts.filter { args == null || it[RawContacts.CONTACT_ID].toString() == args[0] }
            "/data" -> when {
                selection!!.startsWith(Data.RAW_CONTACT_ID) -> data.filter { it[Data.RAW_CONTACT_ID].toString() == args!![0] }
                selection.startsWith(Data.MIMETYPE) -> data.filter { it[Data.MIMETYPE] in args!! }
                else -> data.filter { it[Data.CONTACT_ID].toString() == args!![0] && it[Data.MIMETYPE] in args.drop(1) }
            }
            else -> error("unexpected $uri")
        }
        val columns = projection ?: rows.flatMap { it.keys }.distinct().toTypedArray()
        return MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
    }

    private fun phoneLookup(number: String, projection: Array<out String>): Cursor {
        val digits = number.filter(Char::isDigit).takeLast(10)
        val match = data.firstOrNull { it[Data.MIMETYPE] == Phone.CONTENT_ITEM_TYPE && (it[Data.DATA1] as String).filter(Char::isDigit).endsWith(digits) }
        val contact = match?.let { row -> contacts.first { it[Contacts._ID] == row[Data.CONTACT_ID] } }
        return MatrixCursor(projection).apply {
            if (contact != null) addRow(projection.map { if (it == "display_name") contact[Contacts.DISPLAY_NAME_PRIMARY] else contact[Contacts._ID] })
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        require(Manifest.permission.WRITE_CONTACTS)
        if (failInserts) throw IllegalStateException("insert rejected")
        val row: MutableMap<String, Any?> = values!!.keySet().associateWith { values.get(it) }.toMutableMap()
        return when (uri.path) {
            "/raw_contacts" -> {
                val id = 1000L + inserted.size
                row[RawContacts._ID] = id
                inserted += row
                Uri.withAppendedPath(uri, id.toString())
            }
            "/data" -> {
                inserted += row
                Uri.withAppendedPath(uri, inserted.size.toString())
            }
            else -> null
        }
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        val contacts = ArrayList<Map<String, Any?>>()
        val rawContacts = ArrayList<Map<String, Any?>>()
        val data = ArrayList<Map<String, Any?>>()
        val inserted = ArrayList<Map<String, Any?>>()
        var failInserts = false

        fun reset() {
            failInserts = false
            inserted.clear()
            contacts.clear()
            rawContacts.clear()
            data.clear()
        }

        fun addContact(
            id: Long,
            name: String,
            phones: List<String> = emptyList(),
            emails: List<String> = emptyList(),
            accounts: List<AccountRef> = listOf(AccountRef("com.google", "me@gmail.com")),
            defaultPhone: String? = null,
            alternative: String = name,
            starred: Boolean = false,
        ) {
            val rawId = id * 10
            contacts += mapOf(
                Contacts._ID to id,
                Contacts.LOOKUP_KEY to "key$id",
                Contacts.DISPLAY_NAME_PRIMARY to name,
                Contacts.DISPLAY_NAME_ALTERNATIVE to alternative,
                Contacts.STARRED to if (starred) 1 else 0,
                Contacts.HAS_PHONE_NUMBER to if (phones.isEmpty()) 0 else 1,
            )
            accounts.forEachIndexed { index, account ->
                rawContacts += mapOf(
                    RawContacts._ID to rawId + index, RawContacts.CONTACT_ID to id,
                    RawContacts.ACCOUNT_TYPE to account.type, RawContacts.ACCOUNT_NAME to account.name,
                )
            }
            phones.forEach { number ->
                data += mapOf(
                    Data._ID to data.size + 1L,
                    Data.RAW_CONTACT_ID to rawId,
                    Data.CONTACT_ID to id,
                    Phone.CONTACT_ID to id,
                    Data.MIMETYPE to Phone.CONTENT_ITEM_TYPE,
                    Data.DATA1 to number,
                    Data.DATA2 to Phone.TYPE_MOBILE,
                    Data.DATA3 to null,
                    Data.IS_SUPER_PRIMARY to if (number == defaultPhone) 1 else 0,
                )
            }
            emails.forEach { address ->
                data += mapOf(
                    Data._ID to data.size + 1L, Data.RAW_CONTACT_ID to rawId, Data.CONTACT_ID to id,
                    Data.MIMETYPE to Email.CONTENT_ITEM_TYPE, Data.DATA1 to address,
                )
            }
        }
    }
}
