package io.github.andreygladkov.inkphone.calllog

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog.Calls

class FakeCallLogProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
        val rows = calls.filter { selection == null || isUnreadMissed(it) }.sortedByDescending { it[Calls.DATE] as Long }
        return MatrixCursor(projection).apply { rows.forEach { row -> addRow(projection!!.map { row[it] }) } }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val matching = calls.filter(::isUnreadMissed)
        matching.forEach { row -> values!!.keySet().forEach { key -> row[key] = values.getAsInteger(key) } }
        return matching.size
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun isUnreadMissed(row: Map<String, Any?>) = row[Calls.TYPE] == Calls.MISSED_TYPE && row[Calls.NEW] == 1 && row[Calls.IS_READ] == 0

    companion object {
        val calls = ArrayList<MutableMap<String, Any?>>()

        fun reset() = calls.clear()

        fun add(number: String, type: Int, date: Long, name: String? = null, unread: Boolean = false, duration: Long = 0) {
            calls += mutableMapOf(
                Calls._ID to calls.size + 1L,
                Calls.NUMBER to number,
                Calls.CACHED_NAME to name,
                Calls.TYPE to type,
                Calls.DATE to date,
                Calls.DURATION to duration,
                Calls.NEW to if (unread) 1 else 0,
                Calls.IS_READ to if (unread) 0 else 1,
            )
        }
    }
}
