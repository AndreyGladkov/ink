package io.github.andreygladkov.inkmessages.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms

/** Changes to stored messages; the telephony provider accepts them only from the default SMS app. */
class MessageEdits(private val resolver: ContentResolver) {
    fun markUnread(message: Message): Boolean {
        val values = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        return runCatching { resolver.update(uriOf(message), values, null, null) > 0 }.getOrDefault(false)
    }

    fun delete(message: Message): Boolean = runCatching { resolver.delete(uriOf(message), null, null) > 0 }.getOrDefault(false)

    /** Deletes every SMS and MMS of the thread in one call, as the conversations URI does for its thread id. */
    fun deleteThread(threadId: Long): Boolean =
        runCatching { resolver.delete(ContentUris.withAppendedId(CONVERSATIONS, threadId), null, null) > 0 }.getOrDefault(false)

    private fun uriOf(message: Message): Uri = ContentUris.withAppendedId(if (message.isMms) Mms.CONTENT_URI else Sms.CONTENT_URI, message.id)

    private companion object {
        val CONVERSATIONS: Uri = Uri.parse("content://mms-sms/conversations")
    }
}
