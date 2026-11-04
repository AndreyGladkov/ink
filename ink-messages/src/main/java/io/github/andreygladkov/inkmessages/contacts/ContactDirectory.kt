package io.github.andreygladkov.inkmessages.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.PhoneLookup

class ContactDirectory(private val resolver: ContentResolver) {
    private val cache = HashMap<String, Participant>()

    fun participant(address: String): Participant = cache.getOrPut(address) { lookup(address) }

    private fun lookup(address: String): Participant {
        if (!isPhoneNumber(address)) return Participant(address)
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        val found = runCatching {
            resolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.STARRED, PhoneLookup._ID, PhoneLookup.LOOKUP_KEY), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val contactUri = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(3))
                Participant(address, cursor.getString(0), isContact = true, isStarred = cursor.getInt(1) == 1, contactUri = contactUri)
            }
        }.getOrNull()
        return found ?: Participant(address)
    }

    companion object {
        fun isPhoneNumber(address: String) = address.any(Char::isDigit) && address.none(Char::isLetter)
    }
}
