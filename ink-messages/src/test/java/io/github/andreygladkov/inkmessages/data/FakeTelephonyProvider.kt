package io.github.andreygladkov.inkmessages.data

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class FakeTelephonyProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sortOrder: String?): Cursor {
        if (context!!.checkCallingOrSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Permission Denial: reading telephony provider requires READ_SMS")
        }
        val table = when ("${uri.authority}/${uri.path.orEmpty().trim('/')}") {
            "mms-sms/conversations" -> {
                require(uri.getQueryParameter("simple") == "true") { "only simple thread queries are faked" }
                threads
            }
            "mms-sms/canonical-addresses" -> addresses
            "sms/" -> sms
            "mms/" -> mms
            "mms/part" -> parts
            else -> error("unexpected uri $uri")
        }
        val rows = table.filter { row -> matches(row, selection) }
        return MatrixCursor(projection).apply { rows.forEach { row -> addRow(projection!!.map { row[it] }) } }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun matches(row: Map<String, Any?>, selection: String?): Boolean =
        selection == null || selection.split(" AND ").all { clause -> clauseMatches(row, clause.trim()) }

    private fun clauseMatches(row: Map<String, Any?>, clause: String): Boolean {
        IN.matchEntire(clause)?.let { match ->
            val values = match.groupValues[2].split(',').map { it.trim().toLong() }
            return (row[match.groupValues[1]] as Number).toLong() in values
        }
        COMPARE.matchEntire(clause)?.let { match ->
            val actual = (row[match.groupValues[1]] as Number).toLong()
            val expected = match.groupValues[3].toLong()
            return if (match.groupValues[2] == "=") actual == expected else actual != expected
        }
        error("unsupported selection clause: $clause")
    }

    companion object {
        private val IN = Regex("""(\w+) IN \(([\d, ]+)\)""")
        private val COMPARE = Regex("""(\w+) (=|!=) (-?\d+)""")

        private val threads = ArrayList<MutableMap<String, Any?>>()
        private val addresses = ArrayList<MutableMap<String, Any?>>()
        private val sms = ArrayList<MutableMap<String, Any?>>()
        private val mms = ArrayList<MutableMap<String, Any?>>()
        private val parts = ArrayList<MutableMap<String, Any?>>()

        fun reset() = listOf(threads, addresses, sms, mms, parts).forEach { it.clear() }

        fun thread(vararg recipients: String): Long {
            val ids = recipients.map { address ->
                addresses.find { it["address"] == address }?.get("_id") as Long?
                    ?: (addresses.size + 1L).also { addresses += mutableMapOf("_id" to it, "address" to address) }
            }
            val id = threads.size + 1L
            threads += mutableMapOf("_id" to id, "recipient_ids" to ids.joinToString(" "))
            return id
        }

        fun sms(threadId: Long, body: String, date: Long, type: Int, read: Boolean = true): Long {
            val id = sms.size + 1L
            sms += mutableMapOf("_id" to id, "thread_id" to threadId, "body" to body, "date" to date, "type" to type, "read" to if (read) 1 else 0)
            return id
        }

        fun mms(threadId: Long, dateSeconds: Long, box: Int, read: Boolean = true, text: String? = null, image: Boolean = false): Long {
            val id = mms.size + 1L
            mms += mutableMapOf("_id" to id, "thread_id" to threadId, "date" to dateSeconds, "msg_box" to box, "read" to if (read) 1 else 0)
            parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "application/smil", "text" to "<smil/>")
            if (image) parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "image/jpeg", "text" to null)
            if (text != null) parts += mutableMapOf("_id" to parts.size + 1L, "mid" to id, "ct" to "text/plain", "text" to text)
            return id
        }
    }
}
