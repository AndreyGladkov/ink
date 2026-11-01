package io.github.andreygladkov.inkphone.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup

object ContactLookup {
    data class Match(val contactId: Long, val name: String)

    fun find(context: Context, number: String): Match? {
        if (number.isBlank() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return context.contentResolver.query(uri, arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) Match(cursor.getLong(0), cursor.getString(1).orEmpty()) else null
        }
    }
}
