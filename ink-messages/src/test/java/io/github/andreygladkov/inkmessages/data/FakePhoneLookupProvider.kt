package io.github.andreygladkov.inkmessages.data

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup

class FakePhoneLookupProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
        if (context!!.checkCallingOrSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Permission Denial: reading contacts requires READ_CONTACTS")
        }
        require(uri.pathSegments.first() == "phone_lookup") { "unexpected uri $uri" }
        val wanted = digits(uri.lastPathSegment.orEmpty())
        val rows = contacts.filter { digits(it.number) == wanted }
        return MatrixCursor(projection).apply {
            rows.forEach { contact ->
                addRow(projection!!.map { column ->
                    when (column) {
                        PhoneLookup.DISPLAY_NAME -> contact.name
                        PhoneLookup.STARRED -> if (contact.starred) 1 else 0
                        else -> error("unexpected column $column")
                    }
                })
            }
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun digits(number: String) = number.filter(Char::isDigit).takeLast(10)

    data class Contact(val number: String, val name: String, val starred: Boolean)

    companion object {
        val contacts = ArrayList<Contact>()

        fun reset() = contacts.clear()

        fun add(number: String, name: String, starred: Boolean = false) {
            contacts += Contact(number, name, starred)
        }
    }
}
