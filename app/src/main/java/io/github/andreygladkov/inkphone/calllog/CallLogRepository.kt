package io.github.andreygladkov.inkphone.calllog

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CallLog.Calls

class CallLogRepository(private val resolver: ContentResolver) {

    fun load(limit: Int = LIMIT): List<CallLogEntry> {
        val uri = Calls.CONTENT_URI.buildUpon().appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        val projection = arrayOf(Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.TYPE, Calls.DATE, Calls.DURATION)
        val entries = ArrayList<CallLogEntry>()
        resolver.query(uri, projection, null, null, "${Calls.DATE} DESC")?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += CallLogEntry(
                    id = cursor.getLong(0),
                    number = cursor.getString(1).orEmpty(),
                    name = cursor.getString(2)?.takeIf { it.isNotBlank() },
                    type = typeOf(cursor.getInt(3)),
                    dateMillis = cursor.getLong(4),
                    durationSeconds = cursor.getLong(5),
                )
            }
        }
        return entries
    }

    fun unreadMissedCount(): Int =
        resolver.query(Calls.CONTENT_URI, arrayOf(Calls._ID), UNREAD_MISSED, null, null)?.use { it.count } ?: 0

    fun markMissedRead() {
        val values = ContentValues().apply {
            put(Calls.NEW, 0)
            put(Calls.IS_READ, 1)
        }
        resolver.update(Calls.CONTENT_URI, values, UNREAD_MISSED, null)
    }

    private fun typeOf(type: Int) = when (type) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.INCOMING
        Calls.OUTGOING_TYPE -> CallType.OUTGOING
        Calls.MISSED_TYPE -> CallType.MISSED
        Calls.REJECTED_TYPE -> CallType.REJECTED
        Calls.BLOCKED_TYPE -> CallType.BLOCKED
        Calls.VOICEMAIL_TYPE -> CallType.VOICEMAIL
        else -> CallType.OTHER
    }

    private companion object {
        const val LIMIT = 500
        const val UNREAD_MISSED = "${Calls.TYPE}=${Calls.MISSED_TYPE} AND ${Calls.NEW}=1 AND ${Calls.IS_READ}=0"
    }
}
