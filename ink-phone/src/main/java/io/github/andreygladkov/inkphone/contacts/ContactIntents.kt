package io.github.andreygladkov.inkphone.contacts

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts

/** Requests from other apps, such as Ink Messages, to show or edit a contact. */
object ContactIntents {
    /** The contact a VIEW or EDIT intent names; INSERT requests and anything outside the contacts provider name none. */
    fun contactUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return null
        return intent.data?.takeIf { it.authority == ContactsContract.AUTHORITY }
    }

    /** Resolves both lookup URIs, which survive contact merges, and plain contact id URIs. */
    fun contactId(resolver: ContentResolver, uri: Uri): Long? =
        runCatching { Contacts.lookupContact(resolver, uri) }.getOrNull()?.let(ContentUris::parseId)
}
